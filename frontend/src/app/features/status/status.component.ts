import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ApiService, DashboardServiceStatus } from '@app/data-access/api.service';

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

  constructor(private readonly api: ApiService) {}

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
    this.api.getHealth().subscribe({
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

    this.dashboardRefreshInFlight = true;
    this.dashboardLoading = true;
    this.serviceStatuses = this.placeholderStatuses();

    try {
      const dashboard = await firstValueFrom(this.api.getDashboardStatus());
      this.serviceStatuses = [
        ...dashboard.services,
        {
          key: 'frontend',
          label: 'Frontend UI',
          state: 'up',
          detail: ''
        }
      ];
      this.lastDashboardCheck = new Date().toLocaleTimeString();
    } catch {
      this.serviceStatuses = this.placeholderStatuses();
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

  private placeholderStatuses(): ServiceStatus[] {
    return [
      this.unknownStatus('gateway', 'Gateway'),
      this.unknownStatus('auth', 'Auth Service'),
      this.unknownStatus('api', 'API Service'),
      this.unknownStatus('audit', 'Audit Service'),
      this.unknownStatus('mailer', 'Mailer Service'),
      this.unknownStatus('notify', 'Notification Service'),
      this.unknownStatus('frontend', 'Frontend UI')
    ];
  }

  private unknownStatus(key: string, label: string): DashboardServiceStatus {
    return {
      key,
      label,
      state: 'unknown',
      detail: 'unreachable or timeout'
    };
  }
}
