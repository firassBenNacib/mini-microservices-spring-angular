import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { environment } from '@environments/environment';

import { AuditService } from './audit.service';

describe('AuditService', () => {
  let service: AuditService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AuditService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests recent audit events using the configured default limit', () => {
    const expected = [{ id: 1, eventType: 'LOGIN_SUCCESS' }];

    service.getRecent().subscribe((events) => {
      expect(events).toEqual(expected);
    });

    const request = httpMock.expectOne(`${environment.auditUrl}/recent?limit=10`);
    expect(request.request.method).toBe('GET');
    request.flush(expected);
  });

  it('requests recent audit events using the supplied limit', () => {
    service.getRecent(25).subscribe();

    const request = httpMock.expectOne(`${environment.auditUrl}/recent?limit=25`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });
});
