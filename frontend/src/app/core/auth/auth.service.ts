import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, catchError, map, of, tap } from 'rxjs';
import { environment } from '@environments/environment';

export interface AuthSession {
  authenticated: boolean;
  expiresIn: number;
  user: {
    email: string;
    role: string;
  } | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly anonymousSession: AuthSession = {
    authenticated: false,
    expiresIn: 0,
    user: null
  };

  private readonly sessionSubject = new BehaviorSubject<AuthSession>(this.anonymousSession);

  readonly session$ = this.sessionSubject.asObservable();

  constructor(private http: HttpClient) {}

  isAuthenticated(): boolean {
    return this.sessionSubject.value.authenticated;
  }

  ensureSession() {
    return this.http.get<AuthSession>(`${environment.authUrl}/session`).pipe(
      tap((session) => this.sessionSubject.next(session)),
      catchError(() => {
        this.sessionSubject.next(this.anonymousSession);
        return of(this.anonymousSession);
      })
    );
  }

  login(email: string, password: string) {
    return this.http
      .post<AuthSession>(`${environment.authUrl}/login`, { email, password })
      .pipe(
        tap((session) => this.sessionSubject.next(session)),
        map(() => void 0)
      );
  }

  logout() {
    return this.http.post(`${environment.authUrl}/logout`, {}).pipe(
      tap(() => this.sessionSubject.next(this.anonymousSession)),
      catchError(() => {
        this.sessionSubject.next(this.anonymousSession);
        return of(void 0);
      }),
      map(() => void 0)
    );
  }
}
