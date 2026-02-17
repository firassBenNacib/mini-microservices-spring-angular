import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '@app/data-access/api.service';

@Component({
  selector: 'app-email',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './email.component.html',
  styleUrls: ['./email.component.css']
})
export class EmailComponent {
  mailTo = '';
  mailSubject = '';
  mailText = '';
  mailStatus = '';
  loading = false;

  constructor(
    private api: ApiService,
    private router: Router
  ) {}

  sendEmail(): void {
    this.mailStatus = '';
    this.loading = true;
    this.api.sendTestEmail({
      to: this.mailTo,
      subject: this.mailSubject,
      text: this.mailText
    }).subscribe({
      next: () => {
        this.loading = false;
        this.mailStatus = 'Email sent successfully.';
      },
      error: () => {
        this.loading = false;
        this.mailStatus = 'Email failed to send.';
      }
    });
  }

  backToStatus(): void {
    this.router.navigateByUrl('/');
  }
}
