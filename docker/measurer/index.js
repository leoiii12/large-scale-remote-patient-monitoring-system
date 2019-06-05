"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const pusher_js_1 = __importDefault(require("pusher-js"));
const request_1 = __importDefault(require("request"));
const rxjs_1 = require("rxjs");
const operators_1 = require("rxjs/operators");
const percentile = require('percentile');
const patientIds = new rxjs_1.Subject();
const newAlerts = new rxjs_1.Subject();
setInterval(() => {
    const options = { method: 'GET', url: (process.env.FH_ENDPOINT || '') + '/api/getRecentPatients' };
    request_1.default(options, (error, response, body) => {
        if (error)
            throw new Error(error);
        const patients = JSON.parse(body);
        patients
            .filter(p => p.count > 0)
            .map(p => p.patient_id)
            .map(patient_id => {
            patientIds.next(patient_id);
        });
    });
}, 1000 * 60);
const pusher = new pusher_js_1.default('075054eb52942fc09220', {
    cluster: 'ap1'
});
patientIds.asObservable()
    .pipe(operators_1.distinct())
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
    .pipe(operators_1.map(a => {
    const now = new Date().getTime();
    const timestamp = a.timestamp;
    return now - timestamp;
}), operators_1.windowTime(60 * 1000), operators_1.map(win => win.pipe(operators_1.reduce((acc, value, index) => {
    acc.push(value);
    return acc;
}, []))), operators_1.mergeAll())
    .subscribe((latencies) => {
    const maxLatency = Math.max(...latencies);
    const minLatency = Math.min(...latencies);
    const avgLatency = latencies.reduce((acc, value) => acc + value, 0) / latencies.length;
    const p80 = percentile(80, latencies);
    const p50 = percentile(50, latencies);
    const p20 = percentile(20, latencies);
    console.log(`[1 min] maxLatency=${maxLatency}, minLatency=${minLatency}, avgLatency=${avgLatency}, p80=${p80}, p50=${p50}, p20=${p20}.`);
});
newAlerts.asObservable()
    .pipe(operators_1.map(a => {
    const now = new Date().getTime();
    const timestamp = a.timestamp;
    return now - timestamp;
}), operators_1.windowTime(15 * 60 * 1000), operators_1.map(win => win.pipe(operators_1.reduce((acc, value, index) => {
    acc.push(value);
    return acc;
}, []))), operators_1.mergeAll())
    .subscribe((latencies) => {
    const maxLatency = Math.max(...latencies);
    const minLatency = Math.min(...latencies);
    const avgLatency = latencies.reduce((acc, value) => acc + value, 0) / latencies.length;
    const p80 = percentile(80, latencies);
    const p50 = percentile(50, latencies);
    const p20 = percentile(20, latencies);
    console.log(`[15 min] maxLatency=${maxLatency}, minLatency=${minLatency}, avgLatency=${avgLatency}, p80=${p80}, p50=${p50}, p20=${p20}.`);
});
