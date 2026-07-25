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
    <div data-testid="welcome-page" class="mx-auto max-w-2xl p-10 text-center">
      @if (tenantName(); as name) {
        <h1
          data-testid="welcome-greeting"
          class="text-2xl font-semibold text-slate-900 dark:text-white"
        >
          {{ 'welcome.tenantGreeting' | transloco: { tenantName: name } }}
        </h1>
      } @else {
        <h1
          data-testid="welcome-greeting"
          class="text-2xl font-semibold text-slate-900 dark:text-white"
        >
          {{ 'welcome.staffGreeting' | transloco }}
        </h1>
      }
      <p class="mt-3 text-slate-600 dark:text-slate-400">{{ 'welcome.pitch' | transloco }}</p>
      @if (tenantName()) {
        <a
          data-testid="welcome-dashboard-link"
          routerLink="/dashboard"
          class="mt-6 inline-flex items-center rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-500"
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
