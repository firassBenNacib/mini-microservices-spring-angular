import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { environment } from '@environments/environment';

import { ApiService } from './api.service';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('fetches dashboard status from the gateway endpoint', () => {
    const expected = {
      services: [
        { key: 'gateway', label: 'Gateway', state: 'up' as const, detail: 'ok' },
      ],
    };

    service.getDashboardStatus().subscribe((response) => {
      expect(response).toEqual(expected);
    });

    const request = httpMock.expectOne(`${environment.gatewayUrl}/status`);
    expect(request.request.method).toBe('GET');
    request.flush(expected);
  });

  it('posts email payload to the API service', () => {
    const payload = {
      to: 'user@example.com',
      subject: 'hello',
      text: 'world',
    };

    service.sendTestEmail(payload).subscribe();

    const request = httpMock.expectOne(`${environment.apiUrl}/send-test-email`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({});
  });
});
