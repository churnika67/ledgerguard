import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ChaosRunRequest, ChaosRunResponse } from './models';

@Injectable({ providedIn: 'root' })
export class ChaosService {
  constructor(private http: HttpClient) {}

  start(request: ChaosRunRequest): Observable<ChaosRunResponse> {
    return this.http.post<ChaosRunResponse>(`${environment.apiBaseUrl}/admin/chaos-runs`, request);
  }

  get(id: string): Observable<ChaosRunResponse> {
    return this.http.get<ChaosRunResponse>(`${environment.apiBaseUrl}/admin/chaos-runs/${id}`);
  }

  listRecent(): Observable<ChaosRunResponse[]> {
    return this.http.get<ChaosRunResponse[]>(`${environment.apiBaseUrl}/admin/chaos-runs`);
  }
}
