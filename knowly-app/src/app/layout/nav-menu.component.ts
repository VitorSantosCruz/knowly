import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Menu } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { PermissionsService } from '../core/permissions.service';
import { GlobalPermissionsService } from '../core/global-permissions.service';
import { ActiveTenantService, TenantMembership } from '../core/active-tenant.service';
import { AuthService } from '../core/auth.service';
import { BrandWordmarkComponent } from '../shared/brand-wordmark.component';

/**
 * A `MenuItem` extended with the fields this sidebar's custom `#item`/
 * `#submenuheader` templates need: `testId`/`tourId` to keep every existing
 * `data-testid`/`data-tour-id` (load-bearing for tests and the onboarding
 * tour) and `labelKey`/`categoryKey` for Transloco translation at render
 * time rather than baking a fixed-locale string into the model.
 */
interface NavMenuItem extends MenuItem {
  labelKey?: string;
  testId?: string;
  tourId?: string;
}

interface NavMenuGroup extends MenuItem {
  categoryKey: string;
  items: NavMenuItem[];
}

const CATEGORY_LABEL_CLASS =
  'px-3 pt-2 pb-1 text-xs font-semibold tracking-wider text-ink-500 uppercase';

@Component({
  selector: 'app-nav-menu',
  imports: [RouterLink, RouterLinkActive, TranslocoPipe, BrandWordmarkComponent, Menu],
  template: `
    @if (authService.isLoggedIn()) {
      <nav data-testid="nav-menu" class="flex h-full flex-col">
        <div class="mb-6 flex items-center px-1">
          <app-brand-wordmark class="text-white" />
        </div>

        <div class="flex flex-1 flex-col gap-1 overflow-y-auto">
          @for (group of overviewGroups(); track group.categoryKey) {
            <p-menu
              [model]="[group]"
              [popup]="false"
              styleClass="w-full border-0 bg-transparent p-0"
            >
              <ng-template #submenuheader let-item>
                <span [class]="categoryLabelClass">{{ item.categoryKey | transloco }}</span>
              </ng-template>
              <ng-template #item let-item>
                <a
                  [attr.data-testid]="item.testId"
                  [attr.data-tour-id]="item.tourId"
                  [routerLink]="item.routerLink"
                  routerLinkActive="active-nav-link"
                  [class]="linkClass"
                >
                  <i [class]="item.icon + ' ' + iconClass" aria-hidden="true"></i>
                  {{ item.labelKey | transloco }}
                </a>
              </ng-template>
            </p-menu>
          }
        </div>

        @if (workspaceGroup(); as group) {
          <div class="mt-4 flex flex-col gap-1 border-t border-ink-800/60 pt-4">
            <p-menu
              [model]="[group]"
              [popup]="false"
              styleClass="w-full border-0 bg-transparent p-0"
            >
              <ng-template #submenuheader let-item>
                <span [class]="categoryLabelClass">{{ item.categoryKey | transloco }}</span>
              </ng-template>
              <ng-template #item let-item>
                <a
                  [attr.data-testid]="item.testId"
                  [attr.data-tour-id]="item.tourId"
                  [routerLink]="item.routerLink"
                  routerLinkActive="active-nav-link"
                  [class]="linkClass"
                >
                  <i [class]="item.icon + ' ' + iconClass" aria-hidden="true"></i>
                  {{ item.labelKey | transloco }}
                </a>
              </ng-template>
            </p-menu>
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

  protected readonly linkClass =
    'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-ink-300/80 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white hover:shadow-[0_0_20px_-8px_var(--color-signal-500)] active:translate-y-0 active:scale-[0.98] dark:text-ink-300/80 [&.active-nav-link]:bg-signal-500/10 [&.active-nav-link]:text-signal-300 [&.active-nav-link]:shadow-[inset_2px_0_0_0_var(--color-signal-500)]';
  protected readonly iconClass = 'h-4 w-4 shrink-0';
  protected readonly categoryLabelClass = CATEGORY_LABEL_CLASS;

  private readonly memberships = signal<TenantMembership[]>([]);
  // 0 memberships (staff, who never hold a real TenantMembership row even after switching
  // into a tenant — see below) needs this link just as much as >1 does: it's their only path
  // to any tenant. Only a single-membership session (already home, nothing to switch to) hides it.
  protected readonly canSwitchTenant = computed(() => this.memberships().length !== 1);

  protected readonly overviewGroups = computed<NavMenuGroup[]>(() => {
    const groups: NavMenuGroup[] = [];

    if (this.permissionsService.has('DASHBOARD_VIEW')) {
      groups.push({
        categoryKey: 'nav.category.overview',
        items: [
          {
            labelKey: 'nav.dashboard',
            testId: 'nav-dashboard',
            icon: 'pi pi-th-large',
            routerLink: '/dashboard',
          },
        ],
      });
    }

    const knowledgeItems: NavMenuItem[] = [];
    if (this.permissionsService.has('ARTICLE_VIEW')) {
      knowledgeItems.push({
        labelKey: 'nav.articles',
        testId: 'nav-articles',
        tourId: 'articles-nav-link',
        icon: 'pi pi-book',
        routerLink: '/articles',
      });
    }
    if (this.permissionsService.has('CONVERSATION_USE')) {
      knowledgeItems.push({
        labelKey: 'nav.conversations',
        testId: 'nav-conversations',
        icon: 'pi pi-comments',
        routerLink: '/conversations',
      });
    }
    if (knowledgeItems.length > 0) {
      groups.push({ categoryKey: 'nav.category.knowledge', items: knowledgeItems });
    }

    if (this.permissionsService.has('TENANT_MEMBER_MANAGE')) {
      groups.push({
        categoryKey: 'nav.category.team',
        items: [
          {
            labelKey: 'nav.members',
            testId: 'nav-members',
            tourId: 'user-management-nav-link',
            icon: 'pi pi-users',
            routerLink: '/members',
          },
        ],
      });
    }

    return groups;
  });

  protected readonly workspaceGroup = computed<NavMenuGroup | null>(() => {
    const items: NavMenuItem[] = [];

    if (this.globalPermissionsService.has('TENANT_CREATE')) {
      items.push({
        labelKey: 'nav.createTenant',
        testId: 'nav-create-tenant',
        icon: 'pi pi-plus',
        routerLink: '/tenants/new',
      });
    }
    if (this.canSwitchTenant()) {
      items.push({
        labelKey: 'nav.switchTenant',
        testId: 'nav-switch-tenant',
        icon: 'pi pi-arrow-right-arrow-left',
        routerLink: '/select-tenant',
      });
    }

    return items.length > 0 ? { categoryKey: 'nav.category.workspace', items } : null;
  });

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
