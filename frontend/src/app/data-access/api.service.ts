import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@environments/environment';

export interface EmailPayload {
  to: string;
  subject: string;
  text: string;
}

export interface DashboardServiceStatus {
  key: string;
  label: string;
  state: 'up' | 'down' | 'unknown';
  detail: string;
}

export interface DashboardStatusResponse {
  services: DashboardServiceStatus[];
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  getHealth() {
    return this.http.get<{ status: string }>(`${environment.apiUrl}/health`);
  }

  getMessage() {
    return this.http.get<{ message: string }>(`${environment.apiUrl}/message`);
  }

  getDashboardStatus() {
    return this.http.get<DashboardStatusResponse>(`${environment.gatewayUrl}/status`);
  }

  sendTestEmail(payload: EmailPayload) {
    return this.http.post(
      `${environment.apiUrl}/send-test-email`,
      payload
    );
  }

  sendTestNotification(payload: EmailPayload) {
    return this.http.post(
      `${environment.apiUrl}/send-test-notification`,
      payload
    );
  }
}
