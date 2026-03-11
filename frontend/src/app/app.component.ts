import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '@app/core/auth/auth.service';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  private readonly destroyRef = inject(DestroyRef);
  isAuthed = false;
  footerTitle = 'Status';
  isLoginRoute = false;

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router
  ) {
    this.isAuthed = this.auth.isAuthenticated();
    this.auth.ensureSession()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe();
    this.auth.session$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((session) => {
        this.isAuthed = session.authenticated;
      });

    this.footerTitle = this.resolveTitle(this.router.url);
    this.isLoginRoute = this.router.url.startsWith('/login');
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((event) => {
        this.footerTitle = this.resolveTitle(event.urlAfterRedirects);
        this.isLoginRoute = event.urlAfterRedirects.startsWith('/login');
      });
  }

  logout(): void {
    this.auth.logout().subscribe(() => {
      this.router.navigateByUrl('/login');
    });
  }

  private resolveTitle(url: string): string {
    if (url.startsWith('/email')) return 'Send Email';
    if (url.startsWith('/notification')) return 'Send Notification';
    if (url.startsWith('/audit')) return 'Audit Events';
    if (url.startsWith('/login')) return 'Sign In';
    return 'Status';
  }
}
