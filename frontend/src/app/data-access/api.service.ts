import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '@environments/environment';

export interface EmailPayload {
  to: string;
  subject: string;
  text: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  getMessage() {
    return this.http.get<{ message: string }>(`${environment.apiUrl}/message`);
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
