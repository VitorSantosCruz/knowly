import { Component, OnInit, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { OnboardingService } from '../../core/onboarding.service';
import { TourService } from '../../core/tour.service';

@Component({
  selector: 'app-welcome-page',
  imports: [TranslocoPipe, RouterLink],
  template: `
    <div
      data-testid="welcome-page"
      class="enter-fluid mx-auto max-w-2xl rounded-2xl border border-ink-200/70 bg-white p-10 text-center shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
    >
      @if (tenantName(); as name) {
        <h1
          data-testid="welcome-greeting"
          class="font-display text-2xl font-semibold tracking-tight text-ink-900 dark:text-white"
        >
          {{ 'welcome.tenantGreeting' | transloco: { tenantName: name } }}
        </h1>
      } @else {
        <h1
          data-testid="welcome-greeting"
          class="font-display text-2xl font-semibold tracking-tight text-ink-900 dark:text-white"
        >
          {{ 'welcome.staffGreeting' | transloco }}
        </h1>
      }
      <p class="mt-3 text-ink-600 dark:text-ink-400">{{ 'welcome.pitch' | transloco }}</p>
      @if (tenantName()) {
        <a
          data-testid="welcome-dashboard-link"
          routerLink="/dashboard"
          class="mt-6 inline-flex items-center rounded-xl bg-ink-800 px-4 py-2 text-sm font-medium text-white shadow-sm shadow-ink-900/20 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-signal-600 hover:shadow-md active:translate-y-0 active:scale-[0.98] active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
        >
          {{ 'welcome.viewDashboard' | transloco }}
        </a>
      }
    </div>
  `,
})
export class WelcomePageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly onboardingService = inject(OnboardingService);
  private readonly tourService = inject(TourService);

  protected readonly tenantName = this.activeTenantService.activeTenantName;

  private hasAutoStarted = false;

  constructor() {
    effect(() => {
      const completed = this.onboardingService.completed();

      if (completed === false && !this.hasAutoStarted) {
        this.hasAutoStarted = true;
        this.tourService.start();
      }
    });
  }

  ngOnInit(): void {
    this.onboardingService.fetch();
    this.activeTenantService.fetch();
  }
}
