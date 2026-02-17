import { Routes } from '@angular/router';
import { authGuard } from '@app/core/auth/auth.guard';
import { LoginComponent } from '@app/features/auth/login/login.component';
import { StatusComponent } from '@app/features/status/status.component';
import { EmailComponent } from '@app/features/email/email.component';
import { AuditComponent } from '@app/features/audit/audit.component';
import { NotificationComponent } from '@app/features/notification/notification.component';

export const appRoutes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: StatusComponent, canActivate: [authGuard] },
  { path: 'email', component: EmailComponent, canActivate: [authGuard] },
  { path: 'notification', component: NotificationComponent, canActivate: [authGuard] },
  { path: 'audit', component: AuditComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
