import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { PermissionsService } from '../core/permissions.service';
import { GlobalPermissionsService } from '../core/global-permissions.service';
import { ActiveTenantService, TenantMembership } from '../core/active-tenant.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-nav-menu',
  imports: [RouterLink, TranslocoPipe],
  template: `
    @if (authService.isLoggedIn()) {
      <nav data-testid="nav-menu" class="fixed top-4 left-4 z-10 flex items-center gap-1">
        @if (permissionsService.has('DASHBOARD_VIEW')) {
          <a
            data-testid="nav-dashboard"
            routerLink="/dashboard"
            class="rounded-full px-3 py-1.5 text-sm hover:bg-slate-200/70 dark:hover:bg-slate-800"
          >
            {{ 'nav.dashboard' | transloco }}
          </a>
        }
        @if (permissionsService.has('ARTICLE_VIEW')) {
          <a
            data-testid="nav-articles"
            data-tour-id="articles-nav-link"
            routerLink="/articles"
            class="rounded-full px-3 py-1.5 text-sm hover:bg-slate-200/70 dark:hover:bg-slate-800"
          >
            {{ 'nav.articles' | transloco }}
          </a>
        }
        @if (permissionsService.has('CONVERSATION_USE')) {
          <a
            data-testid="nav-conversations"
            routerLink="/conversations"
            class="rounded-full px-3 py-1.5 text-sm hover:bg-slate-200/70 dark:hover:bg-slate-800"
          >
            {{ 'nav.conversations' | transloco }}
          </a>
        }
        @if (permissionsService.has('TENANT_MEMBER_MANAGE')) {
          <a
            data-testid="nav-members"
            data-tour-id="user-management-nav-link"
            routerLink="/members"
            class="rounded-full px-3 py-1.5 text-sm hover:bg-slate-200/70 dark:hover:bg-slate-800"
          >
            {{ 'nav.members' | transloco }}
          </a>
        }
        @if (globalPermissionsService.has('TENANT_CREATE')) {
          <a
            data-testid="nav-create-tenant"
            routerLink="/tenants/new"
            class="rounded-full px-3 py-1.5 text-sm hover:bg-slate-200/70 dark:hover:bg-slate-800"
          >
            {{ 'nav.createTenant' | transloco }}
          </a>
        }
        @if (canSwitchTenant()) {
          <a
            data-testid="nav-switch-tenant"
            routerLink="/select-tenant"
            class="rounded-full px-3 py-1.5 text-sm hover:bg-slate-200/70 dark:hover:bg-slate-800"
          >
            {{ 'nav.switchTenant' | transloco }}
          </a>
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
