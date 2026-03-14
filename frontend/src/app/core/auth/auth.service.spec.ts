import { HttpClient } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let http: jest.Mocked<Pick<HttpClient, 'get' | 'post'>>;
  let service: AuthService;

  beforeEach(() => {
    http = {
      get: jest.fn(),
      post: jest.fn(),
    };
    service = new AuthService(http as unknown as HttpClient);
  });

  it('refreshes the session when the access session is anonymous', async () => {
    http.get.mockReturnValue(of({ authenticated: false, expiresIn: 0, user: null }));
    http.post.mockReturnValue(
      of({
        authenticated: true,
        expiresIn: 900,
        user: { email: 'user@example.com', role: 'admin' }
      })
    );

    const session = await firstValueFrom(service.ensureSession());

    expect(session.authenticated).toBe(true);
    expect(http.get).toHaveBeenCalledWith('/auth/session');
    expect(http.post).toHaveBeenCalledWith('/auth/refresh', {});
    expect(service.isAuthenticated()).toBe(true);
  });

  it('login updates the in-memory session', async () => {
    http.get.mockReturnValue(of({ authenticated: false, expiresIn: 0, user: null }));
    http.post.mockReturnValue(
      of({
        authenticated: true,
        expiresIn: 900,
        user: { email: 'user@example.com', role: 'user' }
      })
    );

    await firstValueFrom(service.login('user@example.com', 'secret'));

    expect(http.get).toHaveBeenCalledWith('/auth/session');
    expect(http.post).toHaveBeenCalledWith('/auth/login', {
      email: 'user@example.com',
      password: 'secret'
    });
    expect(service.isAuthenticated()).toBe(true);
  });

  it('logout clears the session even when the backend call fails', async () => {
    http.post.mockImplementation((url: string) => {
      if (url === '/auth/logout') {
        return throwError(() => new Error('logout failed'));
      }
      return of({ authenticated: false, expiresIn: 0, user: null });
    });

    await firstValueFrom(service.logout());

    expect(service.isAuthenticated()).toBe(false);
  });
});
