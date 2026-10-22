import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PageResponse, RejectReason, TransferResponse, TransferStatus } from './models';

@Injectable({ providedIn: 'root' })
export class TransfersService {
  constructor(private http: HttpClient) {}

  list(
    page: number,
    size: number,
    status: TransferStatus | null,
    rejectReason: RejectReason | null,
  ): Observable<PageResponse<TransferResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    if (rejectReason) {
      params = params.set('rejectReason', rejectReason);
    }
    return this.http.get<PageResponse<TransferResponse>>(`${environment.apiBaseUrl}/admin/transfers`, { params });
  }
}
