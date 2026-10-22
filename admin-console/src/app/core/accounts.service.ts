import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AccountResponse, PageResponse } from './models';

@Injectable({ providedIn: 'root' })
export class AccountsService {
  constructor(private http: HttpClient) {}

  list(page: number, size: number, sort: string, q: string | null): Observable<PageResponse<AccountResponse>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<PageResponse<AccountResponse>>(`${environment.apiBaseUrl}/admin/accounts`, { params });
  }
}
