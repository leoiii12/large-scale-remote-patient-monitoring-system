import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class RuntimeEnvService {

  private env: BehaviorSubject<{ apiUrl: string }> = new BehaviorSubject(null);

  constructor(private http: HttpClient) {
  }

  getEnv(): Observable<{ apiUrl: string }> {
    if (this.env.getValue() === null) {
      return this.http
        .get<{ apiUrl: string }>('assets/env.json')
        .pipe(
          map((env) => {
            this.env.next(env);

            return env;
          })
        );
    }

    return this.env.asObservable();
  }


}
