import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { environment } from '../../environments/environment';

function base64url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fakeJwt(payload: Record<string, unknown>): string {
  const header = base64url(JSON.stringify({ alg: 'none' }));
  const body = base64url(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('starts logged out when localStorage has no token', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.isAdmin()).toBeFalse();
  });

  it('stores the token and exposes admin/username claims after a successful login', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const token = fakeJwt({ sub: 'admin', roles: ['ROLE_ADMIN'], exp: futureExp });

    service.login({ username: 'admin', password: 'admin123' }).subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/token`);
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 3600 });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.isAdmin()).toBeTrue();
    expect(service.username()).toBe('admin');
    expect(localStorage.getItem('ledgerguard_admin_token')).toBe(token);
  });

  it('does not grant isAdmin for a USER-role token', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const token = fakeJwt({ sub: 'alice', roles: ['ROLE_USER'], exp: futureExp });

    service.login({ username: 'alice', password: 'password123' }).subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/token`).flush({
      accessToken: token,
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.isAdmin()).toBeFalse();
  });

  it('treats an expired token as not authenticated', () => {
    const pastExp = Math.floor(Date.now() / 1000) - 10;
    localStorage.setItem('ledgerguard_admin_token', fakeJwt({ sub: 'admin', roles: ['ROLE_ADMIN'], exp: pastExp }));

    const fresh = TestBed.inject(AuthService);

    expect(fresh.isAuthenticated()).toBeFalse();
  });

  it('logout clears the token and localStorage', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const token = fakeJwt({ sub: 'admin', roles: ['ROLE_ADMIN'], exp: futureExp });
    service.login({ username: 'admin', password: 'admin123' }).subscribe();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/token`).flush({
      accessToken: token,
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
    });

    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('ledgerguard_admin_token')).toBeNull();
  });
});
