import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthService } from './auth.service';

describe('adminGuard', () => {
  let authStub: { isAuthenticated: jasmine.Spy; isAdmin: jasmine.Spy };
  let router: Router;

  beforeEach(() => {
    authStub = {
      isAuthenticated: jasmine.createSpy('isAuthenticated'),
      isAdmin: jasmine.createSpy('isAdmin'),
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authStub }],
    });
    router = TestBed.inject(Router);
  });

  function runGuard(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any)) as boolean | UrlTree;
  }

  it('allows navigation when authenticated and an admin', () => {
    authStub.isAuthenticated.and.returnValue(true);
    authStub.isAdmin.and.returnValue(true);

    expect(runGuard()).toBeTrue();
  });

  it('redirects to /login when not authenticated', () => {
    authStub.isAuthenticated.and.returnValue(false);
    authStub.isAdmin.and.returnValue(false);

    const result = runGuard();

    expect(result).not.toBeTrue();
    expect((result as UrlTree).toString()).toContain('/login');
  });

  it('redirects to /login when authenticated but not an admin', () => {
    authStub.isAuthenticated.and.returnValue(true);
    authStub.isAdmin.and.returnValue(false);

    const result = runGuard();

    expect(result).not.toBeTrue();
    expect((result as UrlTree).toString()).toContain('/login');
  });
});
