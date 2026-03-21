import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app/app.component';
import { appRoutes } from './app/app.routes';
import { authInterceptor } from './app/core/interceptors/auth.interceptor';
import { backendCredentialsInterceptor } from './app/core/interceptors/backend-credentials.interceptor';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(withInterceptors([backendCredentialsInterceptor, authInterceptor]), withXsrfConfiguration({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-XSRF-TOKEN'
    })),
    provideRouter(appRoutes)
  ]
}).catch((err) => console.error(err)); // NOSONAR: Angular's configured browser targets do not allow top-level await here.
