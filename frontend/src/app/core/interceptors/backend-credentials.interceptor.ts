import { HttpInterceptorFn } from '@angular/common/http';
import { environment } from '@environments/environment';

const backendBaseUrls = [
  environment.authUrl,
  environment.apiUrl,
  environment.auditUrl,
  environment.gatewayUrl
];

function isBackendRequest(url: string): boolean {
  return backendBaseUrls.some((baseUrl) => url === baseUrl || url.startsWith(`${baseUrl}/`) || url.startsWith(`${baseUrl}?`));
}

function readCookie(name: string): string | null {
  if (typeof document === 'undefined' || !document.cookie) {
    return null;
  }

  const prefix = `${name}=`;
  for (const cookie of document.cookie.split(';')) {
    const trimmedCookie = cookie.trim();
    if (trimmedCookie.startsWith(prefix)) {
      return decodeURIComponent(trimmedCookie.slice(prefix.length));
    }
  }

  return null;
}

export const backendCredentialsInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isBackendRequest(req.url)) {
    return next(req);
  }

  let headers = req.headers;
  if (!['GET', 'HEAD', 'OPTIONS'].includes(req.method)) {
    const csrfToken = readCookie('XSRF-TOKEN');
    if (csrfToken) {
      headers = headers.set('X-XSRF-TOKEN', csrfToken);
    }
  }

  return next(req.clone({
    headers,
    withCredentials: true
  }));
};
