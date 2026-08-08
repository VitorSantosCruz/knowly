import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import {
  LucideArrowRightLeft,
  LucideBookOpen,
  LucideLayoutGrid,
  LucideLogOut,
  LucideMessagesSquare,
  LucidePanelLeftClose,
  LucidePanelLeftOpen,
  LucidePlus,
  LucideShieldCheck,
  LucideUserPen,
  LucideUsers,
} from '@lucide/angular';
import { PermissionsService } from '../core/permissions.service';
import { GlobalPermissionsService } from '../core/global-permissions.service';
import { ALL_GLOBAL_PERMISSIONS } from '../core/global-permission';
import { ActiveTenantService, TenantMembership } from '../core/active-tenant.service';
import { AuthService } from '../core/auth.service';
import { SidebarStateService } from '../core/sidebar-state.service';
import { BrandWordmarkComponent } from '../shared/brand-wordmark.component';
import { ErrorStateComponent } from '../shared/error-state.component';

type NavIconName =
  | 'layout-grid'
  | 'book-open'
  | 'messages-square'
  | 'users'
  | 'user-pen'
  | 'shield-check'
  | 'plus'
  | 'swap'
  | 'log-out';

/**
 * A nav item carrying the fields this sidebar's template needs:
 * `testId`/`tourId` to keep every existing `data-testid`/`data-tour-id`
 * (load-bearing for tests and the onboarding tour) and `labelKey`/
 * `categoryKey` for Transloco translation at render time rather than
 * baking a fixed-locale string into the model. Replaces the previous
 * `primeng/api` `MenuItem` extension now that `p-menu` is gone.
 *
 * `routerLink` is optional and `onClick` is new: the first workspace-group
 * item that's an action rather than a navigation target (Leave tenant)
 * needs no route, just a handler run on click.
 */
interface NavMenuItem {
  labelKey: string;
  testId?: string;
  tourId?: string;
  icon: NavIconName;
  routerLink?: string;
  onClick?: () => void;
}

interface NavMenuGroup {
  categoryKey: string;
  items: NavMenuItem[];
}

const CATEGORY_LABEL_CLASS =
  'px-3 pt-2 pb-1 text-xs font-semibold tracking-wider text-ink-500 uppercase';

@Component({
  selector: 'app-nav-menu',
  imports: [
    RouterLink,
    RouterLinkActive,
    TranslocoPipe,
    BrandWordmarkComponent,
    ErrorStateComponent,
    LucideLayoutGrid,
    LucideBookOpen,
    LucideMessagesSquare,
    LucideUsers,
    LucideUserPen,
    LucideShieldCheck,
    LucidePlus,
    LucideArrowRightLeft,
    LucideLogOut,
    LucidePanelLeftClose,
    LucidePanelLeftOpen,
  ],
  template: `
    @if (authService.isLoggedIn()) {
      <nav id="nav-menu" data-testid="nav-menu" class="flex h-full flex-col">
        <a
          routerLink="/welcome"
          [class]="
            'mb-6 flex items-center ' + (sidebarState.collapsed() ? 'justify-center px-0' : 'px-1')
          "
        >
          <app-brand-wordmark
            class="text-white"
            [compact]="sidebarState.collapsed()"
            heightClass="h-10"
          />
        </a>

        <div class="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto">
          @for (group of overviewGroups(); track group.categoryKey) {
            <ul class="w-full border-0 bg-transparent p-0">
              <li>
                <span [class]="sidebarState.collapsed() ? 'sr-only' : categoryLabelClass">{{
                  group.categoryKey | transloco
                }}</span>
              </li>
              @for (item of group.items; track item.testId) {
                <li>
                  <a
                    [attr.data-testid]="item.testId"
                    [attr.data-tour-id]="item.tourId"
                    [routerLink]="item.routerLink"
                    routerLinkActive="active-nav-link"
                    [class]="linkClass"
                    (mouseenter)="onNavItemHover($event, item.labelKey)"
                    (mouseleave)="onNavItemUnhover()"
                    (focus)="onNavItemHover($event, item.labelKey)"
                    (blur)="onNavItemUnhover()"
                  >
                    @switch (item.icon) {
                      @case ('layout-grid') {
                        <svg lucideLayoutGrid [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('book-open') {
                        <svg lucideBookOpen [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('messages-square') {
                        <svg lucideMessagesSquare [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('users') {
                        <svg lucideUsers [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('user-pen') {
                        <svg lucideUserPen [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('shield-check') {
                        <svg lucideShieldCheck [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('plus') {
                        <svg lucidePlus [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('swap') {
                        <svg lucideArrowRightLeft [class]="iconClass" aria-hidden="true"></svg>
                      }
                      @case ('log-out') {
                        <svg lucideLogOut [class]="iconClass" aria-hidden="true"></svg>
                      }
                    }
                    <span [class]="sidebarState.collapsed() ? 'sr-only' : ''">{{
                      item.labelKey | transloco
                    }}</span>
                  </a>
                </li>
              }
            </ul>
          }

          @if (workspaceGroup(); as group) {
            <div class="mt-4 flex flex-col gap-1 border-t border-ink-800/60 pt-4">
              @if (leaveTenantError() === 'network') {
                <app-error-state />
              }
              <ul class="w-full border-0 bg-transparent p-0">
                <li>
                  <span [class]="sidebarState.collapsed() ? 'sr-only' : categoryLabelClass">{{
                    group.categoryKey | transloco
                  }}</span>
                </li>
                @for (item of group.items; track item.testId) {
                  <li>
                    @if (item.onClick) {
                      <button
                        type="button"
                        [attr.data-testid]="item.testId"
                        [attr.data-tour-id]="item.tourId"
                        [class]="linkClass"
                        (click)="item.onClick()"
                        (mouseenter)="onNavItemHover($event, item.labelKey)"
                        (mouseleave)="onNavItemUnhover()"
                        (focus)="onNavItemHover($event, item.labelKey)"
                        (blur)="onNavItemUnhover()"
                      >
                        @switch (item.icon) {
                          @case ('layout-grid') {
                            <svg lucideLayoutGrid [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('book-open') {
                            <svg lucideBookOpen [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('messages-square') {
                            <svg lucideMessagesSquare [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('users') {
                            <svg lucideUsers [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('plus') {
                            <svg lucidePlus [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('swap') {
                            <svg lucideArrowRightLeft [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('log-out') {
                            <svg lucideLogOut [class]="iconClass" aria-hidden="true"></svg>
                          }
                        }
                        <span [class]="sidebarState.collapsed() ? 'sr-only' : ''">{{
                          item.labelKey | transloco
                        }}</span>
                      </button>
                    } @else {
                      <a
                        [attr.data-testid]="item.testId"
                        [attr.data-tour-id]="item.tourId"
                        [routerLink]="item.routerLink"
                        routerLinkActive="active-nav-link"
                        [class]="linkClass"
                        (mouseenter)="onNavItemHover($event, item.labelKey)"
                        (mouseleave)="onNavItemUnhover()"
                        (focus)="onNavItemHover($event, item.labelKey)"
                        (blur)="onNavItemUnhover()"
                      >
                        @switch (item.icon) {
                          @case ('layout-grid') {
                            <svg lucideLayoutGrid [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('book-open') {
                            <svg lucideBookOpen [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('messages-square') {
                            <svg lucideMessagesSquare [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('users') {
                            <svg lucideUsers [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('plus') {
                            <svg lucidePlus [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('swap') {
                            <svg lucideArrowRightLeft [class]="iconClass" aria-hidden="true"></svg>
                          }
                          @case ('log-out') {
                            <svg lucideLogOut [class]="iconClass" aria-hidden="true"></svg>
                          }
                        }
                        <span [class]="sidebarState.collapsed() ? 'sr-only' : ''">{{
                          item.labelKey | transloco
                        }}</span>
                      </a>
                    }
                  </li>
                }
              </ul>
            </div>
          }
        </div>

        <button
          type="button"
          data-testid="nav-collapse-toggle"
          [class]="toggleButtonClass + ' mt-auto'"
          [attr.aria-expanded]="!sidebarState.collapsed()"
          aria-controls="nav-menu"
          (click)="sidebarState.toggle()"
        >
          @if (sidebarState.collapsed()) {
            <svg lucidePanelLeftOpen [class]="iconClass" aria-hidden="true"></svg>
          } @else {
            <svg lucidePanelLeftClose [class]="iconClass" aria-hidden="true"></svg>
          }
          <span [class]="sidebarState.collapsed() ? 'sr-only' : ''">{{
            (sidebarState.collapsed() ? 'nav.expand' : 'nav.collapse') | transloco
          }}</span>
        </button>

        <!--
          Rendered as a direct child of nav, NOT nested inside the scrollable overview div
          above: that div has overflow-y-auto, and per the CSS overflow spec, an axis left as
          visible computes to auto as soon as the other axis isn't visible — so any descendant
          that visually pokes out past the collapsed 72px rail (this tooltip's whole purpose)
          silently forces a phantom horizontal scrollbar onto the entire nav, at rest, not just
          while hovering. Being outside that div's subtree entirely — not merely position:fixed,
          which still counts toward an ancestor's scrollable-overflow region if it's DOM-nested
          inside it — is what actually keeps it out of that measurement. Position is computed in
          JS (onNavItemHover) off the hovered/focused item's real bounding rect, since a pure-CSS
          left:100% would need this element nested inside the item it's labeling.
        -->
        @if (hoveredTooltip(); as tooltip) {
          <span
            data-testid="nav-tooltip"
            class="fixed z-50 -translate-y-1/2 rounded-md bg-ink-800 px-2 py-1 text-xs whitespace-nowrap text-white shadow-lg"
            [style.top.px]="tooltip.top"
            [style.left.px]="tooltip.left"
          >
            {{ tooltip.labelKey | transloco }}
          </span>
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
  private readonly router = inject(Router);

  protected readonly linkClass =
    'relative flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-ink-300/80 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-ink-800/60 hover:text-white hover:shadow-[0_0_20px_-8px_var(--color-signal-500)] active:translate-y-0 active:scale-[0.98] dark:text-ink-300/80 [&.active-nav-link]:bg-signal-500/10 [&.active-nav-link]:text-signal-300 [&.active-nav-link]:shadow-[inset_2px_0_0_0_var(--color-signal-500)]';
  // A real getter (not a plain field) so every existing `[class]="iconClass"` binding keeps
  // working unchanged while still reacting to sidebarState.collapsed() — collapsed icons render
  // noticeably bigger (h-5) since they're now the only visual content of their row, no label
  // text alongside to anchor the eye at the smaller h-4 size.
  protected get iconClass(): string {
    return this.sidebarState.collapsed() ? 'h-5 w-5 shrink-0' : 'h-4 w-4 shrink-0';
  }
  protected readonly categoryLabelClass = CATEGORY_LABEL_CLASS;
  protected readonly sidebarState = inject(SidebarStateService);

  // A single floating tooltip (rendered outside the scrollable nav-items div, see the template
  // comment above it) rather than one-per-item: only one can ever be visible at a time (hover/
  // focus is exclusive), and this is what lets its position escape that div's overflow-y-auto
  // subtree entirely instead of just being clipped/measured differently within it.
  protected readonly hoveredTooltip = signal<{
    top: number;
    left: number;
    labelKey: string;
  } | null>(null);

  protected onNavItemHover(event: Event, labelKey: string): void {
    if (!this.sidebarState.collapsed()) {
      return;
    }
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.hoveredTooltip.set({ top: rect.top + rect.height / 2, left: rect.right + 8, labelKey });
  }

  protected onNavItemUnhover(): void {
    this.hoveredTooltip.set(null);
  }
  // mt-auto (appended where used, not baked in here) is what pins this to the very bottom of
  // the nav — the scrollable list/workspace-group above it takes only the space it needs, and
  // this button absorbs all the rest, rather than the old flex-1-on-the-list approach, which
  // pushed the workspace group down with it too (only the toggle itself should behave that way).
  protected readonly toggleButtonClass =
    'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-ink-300/80 transition-all duration-fast ease-fluid hover:bg-ink-800/60 hover:text-white focus-visible:ring-2 focus-visible:ring-signal-500 focus-visible:ring-offset-2 focus-visible:ring-offset-ink-950';

  protected readonly leaveTenantError = signal<'network' | null>(null);

  private readonly memberships = signal<TenantMembership[]>([]);
  // >1 is always true regardless of account shape (a real member's only path to switch, and
  // also true for a STAFF account that atypically holds more than one real membership).
  // 0 memberships (the canonical staff shape, who never hold a real TenantMembership row even
  // after switching into a tenant — see below) resolves off TENANT_ACT_AS_ANY (REQ-10/REQ-11):
  // only a STAFF user actually granted the tenant-list permission gets the full-listing item: a
  // plain MEMBER never has 0 memberships in the first place, so this never wrongly shows for one.
  // A length-1 session is "already home" by default (matches the plain-MEMBER case) UNLESS
  // isStaffAccount() is true — the atypical "STAFF holding exactly one real membership" case
  // (staff-rbac-split REQ-9's isStaffAccount field), which still needs the item to enter that
  // one tenant and leave back to the staff area.
  protected readonly canSwitchTenant = computed(() => {
    const length = this.memberships().length;
    if (length > 1) {
      return true;
    }
    if (length === 1) {
      return this.globalPermissionsService.isStaffAccount();
    }
    return this.globalPermissionsService.has('TENANT_ACT_AS_ANY');
  });

  // Zero (or, per staff-rbac-split's isStaffAccount, an atypical length-1 STAFF account) is a
  // strictly stronger condition than canSwitchTenant's shape: a multi-membership regular member
  // (length > 1) must never see this, so it's checked directly rather than derived from
  // canSwitchTenant. activeTenantId() is read off the service's own signal, not off memberships
  // (which a staff session acting as a tenant never populates).
  protected readonly canLeaveTenant = computed(() => {
    const length = this.memberships().length;
    const eligibleMembershipShape =
      length === 0 || (length === 1 && this.globalPermissionsService.isStaffAccount());
    return eligibleMembershipShape && this.activeTenantService.activeTenantId() !== null;
  });

  // Fourth occurrence of this page-local computed, precedented by staff-global-dashboard's
  // PLAN (StaffDirectoryPageComponent/WelcomePageComponent/OwnProfilePageComponent) — not
  // extracted, same accepted tradeoff.
  private readonly viewerIsStaffAdmin = computed(() =>
    ALL_GLOBAL_PERMISSIONS.every((permission) => this.globalPermissionsService.has(permission)),
  );

  // REQ-19 ("anywhere", not just the active tenant): backed by PermissionsService's
  // any-tenant endpoint, evaluated across every membership server-side — replaces the
  // previously-accepted active-tenant-only gap this comment used to document.
  protected readonly canSeeProfileEditRequests = computed(
    () =>
      this.permissionsService.hasInAnyTenant('PROFILE_EDIT') ||
      this.globalPermissionsService.has('PROFILE_EDIT') ||
      this.memberships().some((membership) => membership.role === 'MEMBER_ADMIN') ||
      this.viewerIsStaffAdmin(),
  );

  // See tenant-access-group-management.guard.ts's doc comment for why this checks
  // activeTenantRole() rather than a tenant Permission -- TENANT_ACCESS_GROUP_VIEW only
  // exists as a GlobalPermission, and a real MEMBER_ADMIN never holds any GlobalPermission.
  protected readonly canSeeTenantAccessGroups = computed(
    () =>
      this.activeTenantService.activeTenantRole() === 'MEMBER_ADMIN' ||
      this.globalPermissionsService.has('TENANT_ACCESS_GROUP_VIEW'),
  );

  protected readonly overviewGroups = computed<NavMenuGroup[]>(() => {
    const groups: NavMenuGroup[] = [];

    if (
      this.permissionsService.has('DASHBOARD_VIEW') ||
      this.globalPermissionsService.has('DASHBOARD_VIEW_GLOBAL')
    ) {
      groups.push({
        categoryKey: 'nav.category.overview',
        items: [
          {
            labelKey: 'nav.dashboard',
            testId: 'nav-dashboard',
            icon: 'layout-grid',
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
        icon: 'book-open',
        routerLink: '/articles',
      });
    }
    if (this.permissionsService.has('CONVERSATION_USE')) {
      knowledgeItems.push({
        labelKey: 'nav.conversations',
        testId: 'nav-conversations',
        icon: 'messages-square',
        routerLink: '/conversations',
      });
    }
    if (knowledgeItems.length > 0) {
      groups.push({ categoryKey: 'nav.category.knowledge', items: knowledgeItems });
    }

    const teamItems: NavMenuItem[] = [];
    if (
      this.permissionsService.has('TENANT_MEMBER_MANAGE') ||
      this.globalPermissionsService.has('STAFF_USER_VIEW')
    ) {
      teamItems.push({
        labelKey: 'nav.members',
        testId: 'nav-members',
        tourId: 'user-management-nav-link',
        icon: 'users',
        routerLink: '/members',
      });
    }
    if (this.canSeeProfileEditRequests()) {
      teamItems.push({
        labelKey: 'profileEditRequests.navLabel',
        testId: 'nav-profile-edit-requests',
        icon: 'users',
        routerLink: '/profile-edit-requests',
      });
    }
    // Route/guard/backend already existed (staffGuard-equivalent accessGroupManagementGuard on
    // STAFF_PERMISSION_MANAGE) — this screen was simply never reachable from anywhere in the nav.
    if (this.globalPermissionsService.has('STAFF_PERMISSION_MANAGE')) {
      teamItems.push({
        labelKey: 'accessGroupManagement.title',
        testId: 'nav-access-groups',
        icon: 'shield-check',
        routerLink: '/staff/access-groups',
      });
    }
    // tenant-access-group-management: mirrors tenantAccessGroupManagementGuard's own
    // MEMBER_ADMIN-bypass-or-GlobalPermission gate, not a tenant Permission check -- see that
    // guard's doc comment for why TENANT_ACCESS_GROUP_VIEW only exists as a GlobalPermission.
    if (this.canSeeTenantAccessGroups()) {
      teamItems.push({
        labelKey: 'accessGroupManagement.title',
        testId: 'nav-tenant-access-groups',
        icon: 'shield-check',
        routerLink: '/tenants/access-groups',
      });
    }
    if (teamItems.length > 0) {
      groups.push({ categoryKey: 'nav.category.team', items: teamItems });
    }

    return groups;
  });

  protected readonly workspaceGroup = computed<NavMenuGroup | null>(() => {
    const items: NavMenuItem[] = [];

    // Also requires no active tenant: a session already inside a tenant should switch or
    // leave, not create a second one from within the workspace it's currently in — holding
    // TENANT_CREATE alone (e.g. a staff admin) isn't enough on its own.
    if (
      this.globalPermissionsService.has('TENANT_CREATE') &&
      this.activeTenantService.activeTenantId() === null
    ) {
      items.push({
        labelKey: 'nav.createTenant',
        testId: 'nav-create-tenant',
        icon: 'plus',
        routerLink: '/tenants/new',
      });
    }
    if (this.canSwitchTenant()) {
      items.push({
        labelKey: 'nav.switchTenant',
        testId: 'nav-switch-tenant',
        icon: 'swap',
        routerLink: '/select-tenant',
      });
    }
    if (this.canLeaveTenant()) {
      items.push({
        labelKey: 'nav.leaveTenant',
        testId: 'nav-leave-tenant',
        icon: 'log-out',
        onClick: () => this.onLeaveTenant(),
      });
    }

    return items.length > 0 ? { categoryKey: 'nav.category.workspace', items } : null;
  });

  // Without this, tenant-scoped permissions (ARTICLE_VIEW/CONVERSATION_USE, driving
  // overviewGroups()) only got fetched once at session start, before any tenant was selected —
  // selecting or leaving a tenant later only updated ActiveTenantService's own signals, leaving
  // Articles/Conversations stale until a full reload re-ran ngOnInit(). Waits for
  // activeTenantResolved() (ActiveTenantService's own "first fetch() has resolved" signal)
  // before reacting at all, and treats the *first* time it resolves as the session-start case
  // ngOnInit() already covers with its own permissionsService.fetch() call — only an
  // activeTenantId() change *after* that first resolution (selectTenant()/leaveTenant(), later
  // in the session) re-fetches, avoiding a redundant double-fetch on initial load.
  private previousActiveTenantId: number | null | undefined = undefined;
  private readonly refetchPermissionsOnTenantChange = effect(() => {
    const resolved = this.activeTenantService.activeTenantResolved();
    const id = this.activeTenantService.activeTenantId();
    if (!resolved) {
      return;
    }
    if (this.previousActiveTenantId === undefined) {
      this.previousActiveTenantId = id;
      return;
    }
    if (id !== this.previousActiveTenantId) {
      this.previousActiveTenantId = id;
      this.permissionsService.fetch();
    }
  });

  protected onLeaveTenant(): void {
    this.leaveTenantError.set(null);
    this.activeTenantService
      .leaveTenant()
      .pipe(
        catchError(() => {
          this.leaveTenantError.set('network');
          return of(null);
        }),
      )
      .subscribe((result) => {
        if (result !== null) {
          this.router.navigateByUrl('/welcome');
        }
      });
  }

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
      // Fetched at session-start alongside the two calls above (not lazily on first nav
      // render) so the inbox link doesn't flash in/out once its own response lands.
      this.permissionsService.fetchInAnyTenant('PROFILE_EDIT');

      this.activeTenantService.list().subscribe((memberships) => {
        this.memberships.set(memberships);
      });
      // Closes a "depends on some other routed page having already called fetch()" gap:
      // the nav menu is a persistent layout component and canLeaveTenant() must reflect
      // activeTenantId() correctly regardless of which page is currently routed.
      this.activeTenantService.fetch();
    });
  }
}
