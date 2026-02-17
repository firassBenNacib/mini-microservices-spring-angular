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

  constructor(
    private auth: AuthService,
    private router: Router
  ) {
    this.isAuthed = this.auth.isAuthenticated();
    this.auth.token$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((token) => {
        this.isAuthed = !!token;
      });

    this.footerTitle = this.resolveTitle(this.router.url);
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((event) => {
        this.footerTitle = this.resolveTitle(event.urlAfterRedirects);
      });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  private resolveTitle(url: string): string {
    if (url.startsWith('/email')) return 'Send Email';
    if (url.startsWith('/notification')) return 'Send Notification';
    if (url.startsWith('/audit')) return 'Audit Events';
    if (url.startsWith('/login')) return 'Sign In';
    return 'Status';
  }
}
