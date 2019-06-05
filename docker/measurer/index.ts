import Pusher from 'pusher-js';
import request from 'request';
import { Subject } from 'rxjs';
import { distinct, map, mergeAll, reduce, windowTime } from 'rxjs/operators';

const percentile = require('percentile');

const patientIds = new Subject<string>();
const newAlerts = new Subject<{ id: string; timestamp: number; unit: string; value: number; }>();

setInterval(() => {
    const options = { method: 'GET', url: (process.env.FH_ENDPOINT || '') + '/api/getRecentPatients' };

    request(options, (error, response, body) => {
        if (error) throw new Error(error);

        const patients: { patient_id: string; count: number; }[] = JSON.parse(body);

        patients
            .filter(p => p.count > 0)
            .map(p => p.patient_id)
            .map(patient_id => {
                patientIds.next(patient_id);
            });
    });
}, 1000 * 60);

const pusher = new Pusher('075054eb52942fc09220', {
    cluster: 'ap1'
});

patientIds.asObservable()
    .pipe(distinct())
    .subscribe(patientId => {
        const channel = pusher.subscribe(patientId);

        channel.bind('newAlert', (context) => {
            newAlerts.next({
                id: context[0],
                timestamp: context[1],
                unit: context[2],
                value: context[3]
            });
        });
    });

newAlerts.asObservable().subscribe(a => {
    const now = new Date().getTime();
    const timestamp = a.timestamp;

    console.log(`Latency=${now - timestamp}`);
});

newAlerts.asObservable()
    .pipe(
        map(a => {
            const now = new Date().getTime();
            const timestamp = a.timestamp;

            return now - timestamp;
        }),
        windowTime(60 * 1000),
        map(win => win.pipe(
            reduce((acc: number[], value: number, index: number) => {
                acc.push(value);

                return acc;
            }, [] as number[])
        )),
        mergeAll()
    )
    .subscribe((latencies: number[]) => {
        const maxLatency = Math.max(...latencies);
        const minLatency = Math.min(...latencies);
        const avgLatency = latencies.reduce((acc, value) => acc + value, 0) / latencies.length;
        const p80 = percentile(80, latencies);
        const p50 = percentile(50, latencies);
        const p20 = percentile(20, latencies);

        console.log(`[1 min] maxLatency=${maxLatency}, minLatency=${minLatency}, avgLatency=${avgLatency}, p80=${p80}, p50=${p50}, p20=${p20}.`);
    });

newAlerts.asObservable()
    .pipe(
        map(a => {
            const now = new Date().getTime();
            const timestamp = a.timestamp;

            return now - timestamp;
        }),
        windowTime(15 * 60 * 1000),
        map(win => win.pipe(
            reduce((acc: number[], value: number, index: number) => {
                acc.push(value);

                return acc;
            }, [] as number[])
        )),
        mergeAll()
    )
    .subscribe((latencies: number[]) => {
        const maxLatency = Math.max(...latencies);
        const minLatency = Math.min(...latencies);
        const avgLatency = latencies.reduce((acc, value) => acc + value, 0) / latencies.length;
        const p80 = percentile(80, latencies);
        const p50 = percentile(50, latencies);
        const p20 = percentile(20, latencies);

        console.log(`[15 min] maxLatency=${maxLatency}, minLatency=${minLatency}, avgLatency=${avgLatency}, p80=${p80}, p50=${p50}, p20=${p20}.`);
    });

