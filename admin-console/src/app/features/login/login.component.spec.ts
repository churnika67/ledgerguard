import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { LoginComponent } from './login.component';
import { environment } from '../../../environments/environment';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent, HttpClientTestingModule, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('navigates to / after a successful admin login', () => {
    spyOn(router, 'navigate');
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const payload = btoa(JSON.stringify({ sub: 'admin', roles: ['ROLE_ADMIN'], exp: futureExp }))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    const token = `eyJhbGciOiJub25lIn0.${payload}.sig`;

    fixture.componentInstance.submit();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/token`).flush({
      accessToken: token,
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
    });

    expect(router.navigate).toHaveBeenCalledWith(['/']);
    expect(fixture.componentInstance.error()).toBeNull();
  });

  it('shows an error and does not navigate when login fails', () => {
    spyOn(router, 'navigate');

    fixture.componentInstance.submit();
    httpMock.expectOne(`${environment.apiBaseUrl}/auth/token`).flush('bad credentials', {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(router.navigate).not.toHaveBeenCalled();
    expect(fixture.componentInstance.error()).toContain('Invalid username or password');
  });
});
