import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ApiService } from '@app/data-access/api.service';
import { environment } from '@environments/environment';

type HealthState = 'up' | 'down' | 'unknown';

interface ServiceStatus {
  key: string;
  label: string;
  state: HealthState;
  detail: string;
}

const DASHBOARD_POLL_INTERVAL_MS = 10000;

@Component({
  selector: 'app-status',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './status.component.html',
  styleUrls: ['./status.component.css']
})
export class StatusComponent implements OnInit, OnDestroy {
  loadingMessage = false;
  statusOk = false;
  dashboardLoading = false;
  lastDashboardCheck = '';
  serviceStatuses: ServiceStatus[] = [];
  private pollTimerId: ReturnType<typeof setInterval> | null = null;
  private dashboardRefreshInFlight = false;
  private readonly visibilityChangeHandler = () => {
    if (document.hidden) {
      this.stopDashboardPolling();
      return;
    }
    void this.refreshDashboard();
    this.startDashboardPolling();
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.checkStatus();
    void this.refreshDashboard();
    this.startDashboardPolling();
    document.addEventListener('visibilitychange', this.visibilityChangeHandler);
  }

  ngOnDestroy(): void {
    this.stopDashboardPolling();
    document.removeEventListener('visibilitychange', this.visibilityChangeHandler);
  }

  checkStatus(): void {
    this.loadingMessage = true;
    this.statusOk = false;
    this.api.getMessage().subscribe({
      next: () => {
        this.statusOk = true;
        this.loadingMessage = false;
      },
      error: () => {
        this.statusOk = false;
        this.loadingMessage = false;
      }
    });
  }

  async refreshDashboard(): Promise<void> {
    if (this.dashboardRefreshInFlight) {
      return;
    }

    const targets = this.getHealthTargets();
    this.dashboardRefreshInFlight = true;
    this.dashboardLoading = true;
    this.serviceStatuses = targets.map((target) => ({
      key: target.key,
      label: target.label,
      state: 'unknown',
      detail: 'checking'
    }));

    try {
      const checks = await Promise.all(targets.map((target) => this.checkService(target)));
      this.serviceStatuses = checks;
      this.lastDashboardCheck = new Date().toLocaleTimeString();
    } finally {
      this.dashboardLoading = false;
      this.dashboardRefreshInFlight = false;
    }
  }

  private startDashboardPolling(): void {
    if (this.pollTimerId !== null || document.hidden) {
      return;
    }
    this.pollTimerId = setInterval(() => {
      void this.refreshDashboard();
    }, DASHBOARD_POLL_INTERVAL_MS);
  }

  private stopDashboardPolling(): void {
    if (this.pollTimerId === null) {
      return;
    }
    clearInterval(this.pollTimerId);
    this.pollTimerId = null;
  }

  private getHealthTargets(): Array<{ key: string; label: string; url?: string }> {
    return [
      { key: 'gateway', label: 'Gateway', url: this.toHealthUrl(environment.gatewayUrl) },
      { key: 'auth', label: 'Auth Service', url: this.toHealthUrl(environment.authUrl) },
      { key: 'api', label: 'API Service', url: this.toHealthUrl(environment.apiUrl) },
      { key: 'audit', label: 'Audit Service', url: this.toHealthUrl(environment.auditUrl) },
      { key: 'mailer', label: 'Mailer Service', url: this.toHealthUrl(environment.mailerUrl) },
      { key: 'notify', label: 'Notification Service', url: this.toHealthUrl(environment.notifyUrl) },
      { key: 'frontend', label: 'Frontend UI' }
    ];
  }

  private toHealthUrl(base: string): string {
    const normalized = (base || '').replace(/\/+$/, '');
    return `${normalized}/health`;
  }

  private async checkService(target: { key: string; label: string; url?: string }): Promise<ServiceStatus> {
    if (!target.url) {
      return {
        key: target.key,
        label: target.label,
        state: 'up',
        detail: ''
      };
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 4000);
    try {
      const response = await fetch(target.url, {
        signal: controller.signal,
        headers: { Accept: 'application/json' }
      });

      if (!response.ok) {
        return {
          key: target.key,
          label: target.label,
          state: 'down',
          detail: `HTTP ${response.status}`
        };
      }

      let detail = 'ok';
      try {
        const body = (await response.json()) as { status?: string };
        if (body && typeof body.status === 'string' && body.status.trim()) {
          detail = body.status;
        }
      } catch {
        detail = 'ok';
      }

      return {
        key: target.key,
        label: target.label,
        state: 'up',
        detail: detail === 'ok' ? '' : detail
      };
    } catch {
      return {
        key: target.key,
        label: target.label,
        state: 'unknown',
        detail: 'unreachable or timeout'
      };
    } finally {
      clearTimeout(timeout);
    }
  }
}
