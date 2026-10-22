import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { jwtDecode } from 'jwt-decode';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthRequest, AuthResponse } from './models';

const STORAGE_KEY = 'ledgerguard_admin_token';

interface TokenClaims {
  sub: string;
  roles: string[];
  exp: number;
}

/**
 * Holds the JWT in a signal (source of truth for the whole app) and mirrors it to
 * localStorage so a page refresh doesn't log the admin out. This is a plain SPA with no
 * server-side component of its own, so there is no httpOnly-cookie option here -- unlike the
 * Next.js customer app's Backend-for-Frontend, the token has to live somewhere the browser's
 * own JavaScript can read it in order to attach it to API calls.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(STORAGE_KEY));

  readonly token = this.tokenSignal.asReadonly();

  readonly claims = computed<TokenClaims | null>(() => {
    const token = this.tokenSignal();
    if (!token) {
      return null;
    }
    try {
      return jwtDecode<TokenClaims>(token);
    } catch {
      return null;
    }
  });

  readonly isAuthenticated = computed(() => {
    const claims = this.claims();
    return !!claims && claims.exp * 1000 > Date.now();
  });

  readonly isAdmin = computed(() => !!this.claims()?.roles?.includes('ROLE_ADMIN'));

  readonly username = computed(() => this.claims()?.sub ?? null);

  constructor(private http: HttpClient) {}

  login(request: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiBaseUrl}/auth/token`, request).pipe(
      tap((response) => {
        localStorage.setItem(STORAGE_KEY, response.accessToken);
        this.tokenSignal.set(response.accessToken);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.tokenSignal.set(null);
  }
}
