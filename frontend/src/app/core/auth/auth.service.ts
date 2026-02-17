import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, map, tap } from 'rxjs';
import { environment } from '@environments/environment';

interface LoginResponse {
  token: string;
  expiresIn: number;
  user: {
    email: string;
    role: string;
  };
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenKey = 'demo_auth_token';
  private readonly tokenSubject = new BehaviorSubject<string | null>(
    localStorage.getItem(this.tokenKey)
  );

  readonly token$ = this.tokenSubject.asObservable();

  constructor(private http: HttpClient) {}

  isAuthenticated(): boolean {
    return !!this.tokenSubject.value;
  }

  getToken(): string | null {
    return this.tokenSubject.value;
  }

  login(email: string, password: string) {
    return this.http
      .post<LoginResponse>(`${environment.authUrl}/login`, { email, password })
      .pipe(
        tap((res) => {
          localStorage.setItem(this.tokenKey, res.token);
          this.tokenSubject.next(res.token);
        }),
        map(() => void 0)
      );
  }

  logout(): void {
    localStorage.removeItem(this.tokenKey);
    this.tokenSubject.next(null);
  }
}
