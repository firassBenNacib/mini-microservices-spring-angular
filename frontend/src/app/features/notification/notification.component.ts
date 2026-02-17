import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '@app/data-access/api.service';

@Component({
  selector: 'app-notification',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notification.component.html',
  styleUrls: ['./notification.component.css']
})
export class NotificationComponent {
  notifyCountryCode = '';
  notifyLocalNumber = '';
  notifyCategory = 'Deployment';
  notifyPriority = 'Info';
  notifyDetails = '';
  notifyStatus = '';
  loading = false;
  readonly categories = ['Deployment', 'Security', 'Incident', 'Reminder'];
  readonly priorities = ['Info', 'Warning', 'Critical'];
  readonly countryCodes = [
    { name: 'Afghanistan', code: '+93' },
    { name: 'Algeria', code: '+213' },
    { name: 'Argentina', code: '+54' },
    { name: 'Australia', code: '+61' },
    { name: 'Austria', code: '+43' },
    { name: 'Bahrain', code: '+973' },
    { name: 'Bangladesh', code: '+880' },
    { name: 'Belgium', code: '+32' },
    { name: 'Brazil', code: '+55' },
    { name: 'Bulgaria', code: '+359' },
    { name: 'Canada', code: '+1' },
    { name: 'Chile', code: '+56' },
    { name: 'China', code: '+86' },
    { name: 'Colombia', code: '+57' },
    { name: 'Croatia', code: '+385' },
    { name: 'Cyprus', code: '+357' },
    { name: 'Czech Republic', code: '+420' },
    { name: 'Denmark', code: '+45' },
    { name: 'Egypt', code: '+20' },
    { name: 'Estonia', code: '+372' },
    { name: 'Finland', code: '+358' },
    { name: 'France', code: '+33' },
    { name: 'Georgia', code: '+995' },
    { name: 'Germany', code: '+49' },
    { name: 'Ghana', code: '+233' },
    { name: 'Greece', code: '+30' },
    { name: 'Hungary', code: '+36' },
    { name: 'Iceland', code: '+354' },
    { name: 'India', code: '+91' },
    { name: 'Indonesia', code: '+62' },
    { name: 'Ireland', code: '+353' },
    { name: 'Israel', code: '+972' },
    { name: 'Italy', code: '+39' },
    { name: 'Japan', code: '+81' },
    { name: 'Jordan', code: '+962' },
    { name: 'Kenya', code: '+254' },
    { name: 'Kuwait', code: '+965' },
    { name: 'Latvia', code: '+371' },
    { name: 'Lebanon', code: '+961' },
    { name: 'Libya', code: '+218' },
    { name: 'Lithuania', code: '+370' },
    { name: 'Luxembourg', code: '+352' },
    { name: 'Malaysia', code: '+60' },
    { name: 'Mexico', code: '+52' },
    { name: 'Morocco', code: '+212' },
    { name: 'Netherlands', code: '+31' },
    { name: 'New Zealand', code: '+64' },
    { name: 'Nigeria', code: '+234' },
    { name: 'Norway', code: '+47' },
    { name: 'Oman', code: '+968' },
    { name: 'Pakistan', code: '+92' },
    { name: 'Philippines', code: '+63' },
    { name: 'Poland', code: '+48' },
    { name: 'Portugal', code: '+351' },
    { name: 'Qatar', code: '+974' },
    { name: 'Romania', code: '+40' },
    { name: 'Russia', code: '+7' },
    { name: 'Saudi Arabia', code: '+966' },
    { name: 'Senegal', code: '+221' },
    { name: 'Singapore', code: '+65' },
    { name: 'Slovakia', code: '+421' },
    { name: 'Slovenia', code: '+386' },
    { name: 'South Africa', code: '+27' },
    { name: 'South Korea', code: '+82' },
    { name: 'Spain', code: '+34' },
    { name: 'Sweden', code: '+46' },
    { name: 'Switzerland', code: '+41' },
    { name: 'Thailand', code: '+66' },
    { name: 'Tunisia', code: '+216' },
    { name: 'Turkey', code: '+90' },
    { name: 'Ukraine', code: '+380' },
    { name: 'United Arab Emirates', code: '+971' },
    { name: 'United Kingdom', code: '+44' },
    { name: 'United States', code: '+1' },
    { name: 'Vietnam', code: '+84' }
  ].sort((a, b) => a.name.localeCompare(b.name));

  constructor(
    private api: ApiService,
    private router: Router
  ) {}

  sendNotification(): void {
    this.notifyStatus = '';
    if (!this.notifyCountryCode) {
      this.notifyStatus = 'Select a country/region.';
      return;
    }
    const recipient = this.computedRecipient;
    if (!this.isValidRecipient(recipient)) {
      this.notifyStatus = 'Invalid number. Select country code and enter digits only.';
      return;
    }

    this.loading = true;
    this.api.sendTestNotification({
      to: recipient,
      subject: this.computedSubject,
      text: this.computedText
    }).subscribe({
      next: () => {
        this.loading = false;
        this.notifyStatus = 'Notification sent successfully.';
      },
      error: () => {
        this.loading = false;
        this.notifyStatus = 'Notification failed to send.';
      }
    });
  }

  backToStatus(): void {
    this.router.navigateByUrl('/');
  }

  get computedSubject(): string {
    return `[${this.notifyPriority}] ${this.notifyCategory} notification`;
  }

  get computedText(): string {
    const details = this.notifyDetails.trim();
    if (details) {
      return details;
    }
    return `${this.notifyCategory} notification (${this.notifyPriority})`;
  }

  get computedRecipient(): string {
    const digits = this.notifyLocalNumber.replace(/\D/g, '');
    return `${this.notifyCountryCode}${digits}`;
  }

  private isValidRecipient(value: string): boolean {
    return /^\+[1-9]\d{7,14}$/.test(value);
  }
}
