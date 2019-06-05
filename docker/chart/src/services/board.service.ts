import * as Pusher from 'pusher-js';
import { Observable, Subject } from 'rxjs';
import { flatMap, map } from 'rxjs/operators';

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { RuntimeEnvService } from './runtime-env.service';

export interface Patient {
  patient_id: string;
  alert_count: string;
}

export interface AggregatedRecord {
  id: string;
  patient_id: string;
  timestamp: number;
  unit: string;
  avg_value: number;
  min_value: number;
  max_value: number;
}

export interface Alert {
  id: string;
  patient_id: string;
  timestamp: number;
  unit: string;
  value: number;
}

export interface AdwinAlert {
  id: string;
  patient_id: string;
  unit: string;
  from: number;
  to: number;
}

@Injectable({
  providedIn: 'root',
})
export class BoardService {

  private newAggregatedRecordSource: Subject<boolean> = new Subject<boolean>();
  public newAggregatedRecordObservable: Observable<boolean> = this.newAggregatedRecordSource.asObservable();

  private newAlertSource: Subject<Alert> = new Subject<Alert>();
  public newAlertObservable: Observable<Alert> = this.newAlertSource.asObservable();

  public aggregatedRecordIds: { [aggregatedRecordId: string]: boolean } = undefined;
  public alertIds: { [alertId: string]: boolean } = undefined;

  private pusher: Pusher.Pusher = new Pusher('075054eb52942fc09220', {
    cluster: 'ap1',
    encrypted: true,
  });
  private patientId: string = undefined;

  constructor(
    private http: HttpClient,
    private runtimeEnv: RuntimeEnvService) {

  }

  public setUpHubForPatient(patientId: string) {
    // Unbind the previous channel and its events
    if (this.patientId) {
      this.pusher.unsubscribe(this.patientId);
    }

    // Bind to the new channel
    this.patientId = patientId;
    this.pusher.subscribe(patientId);

    this.pusher.bind('newRecords', () => {
      console.log(`${patientId} - newRecords`);

      this.newAggregatedRecordSource.next(true);
    });

    this.pusher.bind('newAlert', (data) => {
      console.log(`${patientId} - newAlert`);

      const id = data[0];
      const timestamp = parseInt(data[1], undefined);
      const unit = data[2];
      const value = data[3];

      this.newAlertSource.next({
        id,
        timestamp,
        unit,
        value,
        patient_id: this.patientId,
      });
    });
  }

  public getPatients(partialPatientId: string): Observable<Patient[]> {
    return this.runtimeEnv
      .getEnv()
      .pipe(
        flatMap((env) => {
          return this.http.get<Patient[]>(`${env.apiUrl}/getPatients`, {
            params: {
              partialPatientId,
            },
          });
        }),
      );
  }

  public getRecentPatients(): Observable<Patient[]> {
    return this.runtimeEnv
      .getEnv()
      .pipe(
        flatMap((env) => {
          return this.http.get<Patient[]>(`${env.apiUrl}/getRecentPatients`);
        }),
      );
  }

  public getPatientAggregatedRecords(patientId: string, from: number = new Date().getTime() - 1000 * 60 * 60 * 3): Observable<AggregatedRecord[]> {
    const options = patientId ? { params: new HttpParams().set('patientId', patientId).set('from', from.toString()) } : {};

    return this.runtimeEnv
      .getEnv()
      .pipe(
        flatMap((env) => {
          // tslint:disable-next-line:max-line-length
          return this.http.get<{ id: string; patient_id: string; ts: string; u: string; max_v: number; avg_v: number; min_v: number; }[]>(`${env.apiUrl}/getPatientRecords`, options);
        }),
        map((records) => {
          return records.map((r) => {
            return {
              id: r.id,
              patient_id: r.patient_id,
              timestamp: parseInt(r.ts, undefined),
              unit: r.u,
              max_value: r.max_v,
              avg_value: r.avg_v,
              min_value: r.min_v,
            };
          });
        }),
      );
  }

  public getPatientAlerts(patientId: string, from: number = new Date().getTime() - 1000 * 60 * 60 * 3): Observable<Alert[]> {
    const options = patientId ? { params: new HttpParams().set('patientId', patientId).set('from', from.toString()) } : {};

    return this.runtimeEnv
      .getEnv()
      .pipe(
        flatMap((env) => {
          // tslint:disable-next-line:max-line-length
          return this.http.get<{ id: string; patient_id: string; ts: string; u: string; v: number; }[]>(`${env.apiUrl}/getPatientAlerts`, options);
        }),
        map((alerts) => {
          return alerts.map((a) => {
            return {
              id: a.id,
              patient_id: a.patient_id,
              timestamp: parseInt(a.ts, undefined),
              unit: a.u,
              value: a.v,
            };
          });
        }),
      );
  }

  public getPatientAdwinAlerts(patientId: string, from: number = new Date().getTime() - 1000 * 60 * 60 * 3): Observable<AdwinAlert[]> {
    const options = patientId ? { params: new HttpParams().set('patientId', patientId).set('from', from.toString()) } : {};

    return this.runtimeEnv
      .getEnv()
      .pipe(
        flatMap((env) => {
          // tslint:disable-next-line:max-line-length
          return this.http.get<{ id: string; patient_id: string; u: string; f: string; t: string; }[]>(`${env.apiUrl}/getPatientAdwinAlerts`, options);
        }),
        map((alerts) => {
          return alerts.map((a) => {
            return {
              id: a.id,
              patient_id: a.patient_id,
              unit: a.u,
              from: parseInt(a.f, undefined),
              to: parseInt(a.t, undefined),
            };
          });
        }),
      );
  }

}
