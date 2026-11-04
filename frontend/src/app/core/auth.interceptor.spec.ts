import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

/** An unsigned token with the claims the app reads; the browser never checks signatures. */
function token(expiresInSeconds: number): string {
  const claims = {
    sub: 'u1',
    email: 'rider@demo.local',
    roles: ['USER'],
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
  };
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function signIn(expiresInSeconds = 3600): void {
    TestBed.inject(AuthService).login('rider@demo.local', 'x').subscribe();
    backend
      .expectOne('/api/v1/auth/login')
      .flush({ accessToken: token(expiresInSeconds), tokenType: 'Bearer', expiresIn: 3600 });
  }

  it('sends the bearer token to the API', () => {
    signIn();
    http.get('/api/v1/accounts').subscribe();
    const request = backend.expectOne('/api/v1/accounts').request;
    expect(request.headers.get('Authorization')).toMatch(/^Bearer header\./);
  });

  it('sends no token once it has expired', () => {
    signIn(-1);
    http.get('/api/v1/accounts').subscribe();
    expect(backend.expectOne('/api/v1/accounts').request.headers.has('Authorization')).toBe(false);
  });

  it('signs out and goes to the login page on a 401', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    signIn();
    http.get('/api/v1/accounts').subscribe({ error: () => {} });
    backend.expectOne('/api/v1/accounts').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(TestBed.inject(AuthService).signedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not sign out when the login itself is rejected', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    TestBed.inject(AuthService)
      .login('rider@demo.local', 'wrong')
      .subscribe({ error: () => {} });
    backend.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).not.toHaveBeenCalled();
  });
});
