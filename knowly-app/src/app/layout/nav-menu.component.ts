import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { PermissionsService } from '../core/permissions.service';
import { GlobalPermissionsService } from '../core/global-permissions.service';
import { ActiveTenantService, TenantMembership } from '../core/active-tenant.service';
import { AuthService } from '../core/auth.service';
import { BrandWordmarkComponent } from '../shared/brand-wordmark.component';

const LINK_CLASS =
  'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-ink-300/80 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white hover:shadow-[0_0_20px_-8px_var(--color-signal-500)] active:translate-y-0 active:scale-[0.98] dark:text-ink-300/80';
const LINK_ACTIVE_CLASS =
  'bg-signal-500/10 text-signal-300 shadow-[inset_2px_0_0_0_var(--color-signal-500)] hover:bg-signal-500/15 hover:text-signal-200 hover:shadow-[inset_2px_0_0_0_var(--color-signal-500),0_0_20px_-8px_var(--color-signal-500)]';
const ICON_CLASS = 'h-4 w-4 shrink-0';

@Component({
  selector: 'app-nav-menu',
  imports: [RouterLink, RouterLinkActive, TranslocoPipe, BrandWordmarkComponent],
  template: `
    @if (authService.isLoggedIn()) {
      <nav data-testid="nav-menu" class="flex h-full flex-col">
        <div class="mb-6 flex items-center px-1">
          <app-brand-wordmark class="text-white" />
        </div>

        <div class="flex flex-1 flex-col gap-1">
          @if (permissionsService.has('DASHBOARD_VIEW')) {
            <a
              data-testid="nav-dashboard"
              routerLink="/dashboard"
              [routerLinkActive]="linkActiveClass"
              [class]="linkClass"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                [class]="iconClass"
                aria-hidden="true"
              >
                <rect x="3" y="3" width="7" height="9" rx="1.5" />
                <rect x="14" y="3" width="7" height="5" rx="1.5" />
                <rect x="14" y="12" width="7" height="9" rx="1.5" />
                <rect x="3" y="16" width="7" height="5" rx="1.5" />
              </svg>
              {{ 'nav.dashboard' | transloco }}
            </a>
          }
          @if (permissionsService.has('ARTICLE_VIEW')) {
            <a
              data-testid="nav-articles"
              data-tour-id="articles-nav-link"
              routerLink="/articles"
              [routerLinkActive]="linkActiveClass"
              [class]="linkClass"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                [class]="iconClass"
                aria-hidden="true"
              >
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" />
              </svg>
              {{ 'nav.articles' | transloco }}
            </a>
          }
          @if (permissionsService.has('CONVERSATION_USE')) {
            <a
              data-testid="nav-conversations"
              routerLink="/conversations"
              [routerLinkActive]="linkActiveClass"
              [class]="linkClass"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                [class]="iconClass"
                aria-hidden="true"
              >
                <path
                  d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5Z"
                />
              </svg>
              {{ 'nav.conversations' | transloco }}
            </a>
          }
          @if (permissionsService.has('TENANT_MEMBER_MANAGE')) {
            <a
              data-testid="nav-members"
              data-tour-id="user-management-nav-link"
              routerLink="/members"
              [routerLinkActive]="linkActiveClass"
              [class]="linkClass"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                [class]="iconClass"
                aria-hidden="true"
              >
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
              {{ 'nav.members' | transloco }}
            </a>
          }
        </div>

        @if (globalPermissionsService.has('TENANT_CREATE') || canSwitchTenant()) {
          <div class="mt-4 flex flex-col gap-1 border-t border-ink-800/60 pt-4">
            @if (globalPermissionsService.has('TENANT_CREATE')) {
              <a
                data-testid="nav-create-tenant"
                routerLink="/tenants/new"
                [routerLinkActive]="linkActiveClass"
                [class]="linkClass"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  [class]="iconClass"
                  aria-hidden="true"
                >
                  <line x1="12" y1="5" x2="12" y2="19" />
                  <line x1="5" y1="12" x2="19" y2="12" />
                </svg>
                {{ 'nav.createTenant' | transloco }}
              </a>
            }
            @if (canSwitchTenant()) {
              <a
                data-testid="nav-switch-tenant"
                routerLink="/select-tenant"
                [routerLinkActive]="linkActiveClass"
                [class]="linkClass"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  [class]="iconClass"
                  aria-hidden="true"
                >
                  <path d="M16 3h5v5" />
                  <path d="M8 21H3v-5" />
                  <path d="M21 3 13 11" />
                  <path d="M3 21l8-8" />
                </svg>
                {{ 'nav.switchTenant' | transloco }}
              </a>
            }
          </div>
        }
      </nav>
    }
  `,
})
export class NavMenuComponent implements OnInit {
  protected readonly authService = inject(AuthService);
  protected readonly permissionsService = inject(PermissionsService);
  protected readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly activeTenantService = inject(ActiveTenantService);

  protected readonly linkClass = LINK_CLASS;
  protected readonly linkActiveClass = LINK_ACTIVE_CLASS;
  protected readonly iconClass = ICON_CLASS;

  private readonly memberships = signal<TenantMembership[]>([]);
  // 0 memberships (staff, who never hold a real TenantMembership row even after switching
  // into a tenant — see below) needs this link just as much as >1 does: it's their only path
  // to any tenant. Only a single-membership session (already home, nothing to switch to) hides it.
  protected readonly canSwitchTenant = computed(() => this.memberships().length !== 1);

  ngOnInit(): void {
    // Resyncs against the real session rather than trusting isLoggedIn()'s in-memory state,
    // which reads false after a page reload even with a still-valid session cookie (e.g.
    // navigating straight to /dashboard by URL rather than through the '' root redirect,
    // which is the only other place that currently resyncs it).
    this.authService.checkSession().subscribe((loggedIn) => {
      if (!loggedIn) {
        return;
      }

      this.globalPermissionsService.fetch();

      // Always attempted, not gated on an active *membership*: staff acting as a tenant (via
      // switchActiveTenant) never gets a real TenantMembership row, only server-side session
      // state, so this list would otherwise never reflect that they're "in" a tenant. The
      // permissions endpoint itself already 403s harmlessly (caught in PermissionsService)
      // when there's genuinely no active tenant, so gating the call added no real safety.
      this.permissionsService.fetch();

      this.activeTenantService.list().subscribe((memberships) => {
        this.memberships.set(memberships);
      });
    });
  }
}
