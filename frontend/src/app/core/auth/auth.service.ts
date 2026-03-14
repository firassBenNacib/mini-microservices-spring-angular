import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
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
  private refreshInFlight$: Observable<AuthSession> | null = null;

  readonly session$ = this.sessionSubject.asObservable();

  constructor(private readonly http: HttpClient) {}

  isAuthenticated(): boolean {
    return this.sessionSubject.value.authenticated;
  }

  ensureSession() {
    return this.http.get<AuthSession>(`${environment.authUrl}/session`).pipe(
      switchMap((session) => session.authenticated ? of(session) : this.refresh()),
      tap((session) => this.sessionSubject.next(session)),
      catchError(() => {
        this.sessionSubject.next(this.anonymousSession);
        return of(this.anonymousSession);
      })
    );
  }

  refresh() {
    this.refreshInFlight$ ??= this.http.post<AuthSession>(`${environment.authUrl}/refresh`, {}).pipe(
      tap((session) => this.sessionSubject.next(session)),
      catchError(() => {
        this.sessionSubject.next(this.anonymousSession);
        return of(this.anonymousSession);
      }),
      finalize(() => {
        this.refreshInFlight$ = null;
      }),
      shareReplay(1)
    );

    return this.refreshInFlight$;
  }

  login(email: string, password: string) {
    return this.http
      .get<AuthSession>(`${environment.authUrl}/session`)
      .pipe(
        catchError(() => of(this.anonymousSession)),
        switchMap(() => this.http.post<AuthSession>(`${environment.authUrl}/login`, { email, password })),
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
