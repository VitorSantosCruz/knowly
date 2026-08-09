import { Component, OnInit, computed, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideBookOpen, LucideQuote, LucideShieldCheck, LucideLock } from '@lucide/angular';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ALL_GLOBAL_PERMISSIONS } from '../../core/global-permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { OnboardingService } from '../../core/onboarding.service';
import { PermissionsService } from '../../core/permissions.service';
import { TourService } from '../../core/tour.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';

@Component({
  selector: 'app-welcome-page',
  imports: [
    TranslocoPipe,
    RouterLink,
    LucideBookOpen,
    LucideQuote,
    LucideShieldCheck,
    LucideLock,
    BrandMarkComponent,
  ],
  template: `
    <div data-testid="welcome-page" class="page-shell">
      <div
        class="enter-fluid rounded-2xl border border-ink-200 bg-white p-8 shadow-sm sm:p-10 dark:border-ink-800 dark:bg-ink-900 dark:shadow-none"
      >
        <app-brand-mark heightClass="h-24" class="mb-4 text-ink-900 dark:text-white" />
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
        <p class="mt-3 text-ink-600 dark:text-ink-400">
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
              data-testid="welcome-conversations-link"
              [routerLink]="['/chat']"
              [queryParams]="{ section: 'articles' }"
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

      <div
        class="enter-fluid mt-6 rounded-2xl border border-ink-200/70 bg-white p-8 shadow-lg shadow-ink-900/5 sm:p-10 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
      >
        <h2 class="font-display text-xl font-semibold tracking-tight text-ink-900 dark:text-white">
          {{ 'welcome.about.title' | transloco }}
        </h2>
        <p class="mt-3 text-ink-600 dark:text-ink-400">
          {{ 'welcome.about.intro' | transloco }}
        </p>
        <div class="mt-6 grid gap-4 sm:grid-cols-2">
          <div class="flex gap-3 rounded-xl bg-ink-50 p-4 dark:bg-ink-800/50">
            <svg
              lucideBookOpen
              class="mt-0.5 size-5 shrink-0 text-signal-600 dark:text-signal-400"
              aria-hidden="true"
            ></svg>
            <div>
              <h3 class="text-sm font-semibold text-ink-900 dark:text-white">
                {{ 'welcome.about.features.articles.title' | transloco }}
              </h3>
              <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                {{ 'welcome.about.features.articles.description' | transloco }}
              </p>
            </div>
          </div>
          <div class="flex gap-3 rounded-xl bg-ink-50 p-4 dark:bg-ink-800/50">
            <svg
              lucideQuote
              class="mt-0.5 size-5 shrink-0 text-signal-600 dark:text-signal-400"
              aria-hidden="true"
            ></svg>
            <div>
              <h3 class="text-sm font-semibold text-ink-900 dark:text-white">
                {{ 'welcome.about.features.citedAnswers.title' | transloco }}
              </h3>
              <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                {{ 'welcome.about.features.citedAnswers.description' | transloco }}
              </p>
            </div>
          </div>
          <div class="flex gap-3 rounded-xl bg-ink-50 p-4 dark:bg-ink-800/50">
            <svg
              lucideShieldCheck
              class="mt-0.5 size-5 shrink-0 text-signal-600 dark:text-signal-400"
              aria-hidden="true"
            ></svg>
            <div>
              <h3 class="text-sm font-semibold text-ink-900 dark:text-white">
                {{ 'welcome.about.features.permissions.title' | transloco }}
              </h3>
              <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                {{ 'welcome.about.features.permissions.description' | transloco }}
              </p>
            </div>
          </div>
          <div class="flex gap-3 rounded-xl bg-ink-50 p-4 dark:bg-ink-800/50">
            <svg
              lucideLock
              class="mt-0.5 size-5 shrink-0 text-signal-600 dark:text-signal-400"
              aria-hidden="true"
            ></svg>
            <div>
              <h3 class="text-sm font-semibold text-ink-900 dark:text-white">
                {{ 'welcome.about.features.isolation.title' | transloco }}
              </h3>
              <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                {{ 'welcome.about.features.isolation.description' | transloco }}
              </p>
            </div>
          </div>
        </div>
      </div>

      @if (showGlobalDashboard()) {
        <div class="mt-6 grid gap-4 sm:grid-cols-3">
          <a
            data-testid="welcome-global-dashboard-link"
            routerLink="/dashboard"
            class="group enter-fluid block transition-all duration-base ease-fluid hover:-translate-y-0.5"
          >
            <div
              class="enter-fluid h-full rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 transition-shadow duration-base ease-fluid hover:border-ink-300 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none dark:hover:border-ink-700 hover:shadow-md"
            >
              <h2 class="text-sm font-semibold text-ink-900 dark:text-white">
                {{ 'welcome.quickLinks.globalDashboard.title' | transloco }}
              </h2>
              <p class="mt-1 text-sm text-ink-500 dark:text-ink-400">
                {{ 'welcome.quickLinks.globalDashboard.description' | transloco }}
              </p>
            </div>
          </a>
        </div>
      }
    </div>
  `,
})
export class WelcomePageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly onboardingService = inject(OnboardingService);
  private readonly permissionsService = inject(PermissionsService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly tourService = inject(TourService);

  protected readonly tenantName = this.activeTenantService.activeTenantName;
  protected readonly showArticles = () => this.permissionsService.has('ARTICLE_VIEW');
  protected readonly showConversations = () => this.permissionsService.has('CONVERSATION_USE');
  protected readonly showMembers = () => this.permissionsService.has('TENANT_MEMBER_MANAGE');

  // Same viewerIsStaffAdmin inference already used in StaffDirectoryPageComponent/
  // GlobalDashboardPageComponent — accepted, precedented repetition (see PLAN.md).
  private readonly viewerIsStaffAdmin = computed(() =>
    ALL_GLOBAL_PERMISSIONS.every((permission) => this.globalPermissionsService.has(permission)),
  );

  protected readonly showGlobalDashboard = computed(
    () =>
      !this.tenantName() &&
      (this.globalPermissionsService.has('DASHBOARD_VIEW_GLOBAL') || this.viewerIsStaffAdmin()),
  );

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
    this.globalPermissionsService.fetch();
  }
}
