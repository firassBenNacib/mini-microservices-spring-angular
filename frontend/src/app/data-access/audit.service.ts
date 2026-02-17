import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@environments/environment';

export interface AuditEvent {
  id: number;
  eventType: string;
  actor?: string;
  details?: string;
  source?: string;
  createdAt?: string;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  constructor(private http: HttpClient) {}

  getRecent(limit = 10) {
    return this.http.get<AuditEvent[]>(
      `${environment.auditUrl}/recent?limit=${limit}`
    );
  }
}
