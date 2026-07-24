import { Component, OnInit, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { OnboardingService } from '../../core/onboarding.service';
import { TourService } from '../../core/tour.service';

@Component({
  selector: 'app-dashboard-page',
  imports: [TranslocoPipe, RouterLink],
  template: `
    <div data-testid="dashboard-page" class="p-6">
      <a data-testid="articles-link" routerLink="/articles" data-tour-id="articles-nav-link">
        {{ 'dashboard.articlesLink' | transloco }}
      </a>
    </div>
  `,
})
export class DashboardPageComponent implements OnInit {
  private readonly onboardingService = inject(OnboardingService);
  private readonly tourService = inject(TourService);

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
  }
}
