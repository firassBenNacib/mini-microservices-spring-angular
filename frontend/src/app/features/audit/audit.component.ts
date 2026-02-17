import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuditService, AuditEvent } from '@app/data-access/audit.service';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit.component.html',
  styleUrls: ['./audit.component.css']
})
export class AuditComponent implements OnInit {
  auditEvents: AuditEvent[] = [];
  loadingAudit = false;

  constructor(
    private audit: AuditService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadAudit();
  }

  loadAudit(): void {
    this.loadingAudit = true;
    this.audit.getRecent(10).subscribe({
      next: (events) => {
        this.auditEvents = events || [];
        this.loadingAudit = false;
      },
      error: () => {
        this.auditEvents = [];
        this.loadingAudit = false;
      }
    });
  }

  backToStatus(): void {
    this.router.navigateByUrl('/');
  }
}
