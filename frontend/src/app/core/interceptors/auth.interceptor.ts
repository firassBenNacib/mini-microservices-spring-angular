import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '@app/core/auth/auth.service';
import { environment } from '@environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthRequest = req.url.startsWith(environment.authUrl);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthRequest) {
        return throwError(() => error);
      }

      return auth.refresh().pipe(
        switchMap((session) => session.authenticated ? next(req) : throwError(() => error)),
        catchError(() => throwError(() => error))
      );
    })
  );
};
