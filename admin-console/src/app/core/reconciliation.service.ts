import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ReconciliationResponse } from './models';

@Injectable({ providedIn: 'root' })
export class ReconciliationService {
  constructor(private http: HttpClient) {}

  check(): Observable<ReconciliationResponse> {
    return this.http.get<ReconciliationResponse>(`${environment.apiBaseUrl}/reconciliation`);
  }
}
