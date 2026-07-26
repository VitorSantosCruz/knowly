import { Component, OnInit, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { OnboardingService } from '../../core/onboarding.service';
import { PermissionsService } from '../../core/permissions.service';
import { TourService } from '../../core/tour.service';

@Component({
  selector: 'app-welcome-page',
  imports: [TranslocoPipe, RouterLink],
  template: `
    <div data-testid="welcome-page" class="page-shell">
      <div
        class="enter-fluid rounded-2xl border border-ink-200 bg-white p-8 shadow-sm sm:p-10 dark:border-ink-800 dark:bg-ink-900 dark:shadow-none"
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
        <p class="mt-3 max-w-2xl text-ink-600 dark:text-ink-400">
          {{ 'welcome.pitch' | transloco }}
        </p>
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

      @if (tenantName() && (showArticles() || showConversations() || showMembers())) {
        <div class="mt-6 grid gap-4 sm:grid-cols-3">
          @if (showArticles()) {
            <a
              routerLink="/articles"
              class="group enter-fluid block transition-all duration-base ease-fluid hover:-translate-y-0.5"
            >
              <div
                class="enter-fluid h-full rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid hover:border-ink-300 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none dark:hover:border-ink-700 hover:shadow-md"
              >
                <h2 class="text-sm font-semibold text-ink-900 dark:text-white">
                  {{ 'welcome.quickLinks.articles.title' | transloco }}
                </h2>
                <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                  {{ 'welcome.quickLinks.articles.description' | transloco }}
                </p>
              </div>
            </a>
          }
          @if (showConversations()) {
            <a
              routerLink="/conversations"
              class="group enter-fluid block transition-all duration-base ease-fluid hover:-translate-y-0.5"
            >
              <div
                class="enter-fluid h-full rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid hover:border-ink-300 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none dark:hover:border-ink-700 hover:shadow-md"
              >
                <h2 class="text-sm font-semibold text-ink-900 dark:text-white">
                  {{ 'welcome.quickLinks.conversations.title' | transloco }}
                </h2>
                <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                  {{ 'welcome.quickLinks.conversations.description' | transloco }}
                </p>
              </div>
            </a>
          }
          @if (showMembers()) {
            <a
              routerLink="/members"
              class="group enter-fluid block transition-all duration-base ease-fluid hover:-translate-y-0.5"
            >
              <div
                class="enter-fluid h-full rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid hover:border-ink-300 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none dark:hover:border-ink-700 hover:shadow-md"
              >
                <h2 class="text-sm font-semibold text-ink-900 dark:text-white">
                  {{ 'welcome.quickLinks.members.title' | transloco }}
                </h2>
                <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                  {{ 'welcome.quickLinks.members.description' | transloco }}
                </p>
              </div>
            </a>
          }
        </div>
      }
    </div>
  `,
})
export class WelcomePageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly onboardingService = inject(OnboardingService);
  private readonly permissionsService = inject(PermissionsService);
  private readonly tourService = inject(TourService);

  protected readonly tenantName = this.activeTenantService.activeTenantName;
  protected readonly showArticles = () => this.permissionsService.has('ARTICLE_VIEW');
  protected readonly showConversations = () => this.permissionsService.has('CONVERSATION_USE');
  protected readonly showMembers = () => this.permissionsService.has('TENANT_MEMBER_MANAGE');

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
