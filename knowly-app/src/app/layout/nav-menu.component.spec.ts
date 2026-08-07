import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { NavMenuComponent } from './nav-menu.component';
import { ActiveTenantService } from '../core/active-tenant.service';
import { ALL_PERMISSIONS } from '../core/permission';
import { SidebarStateService } from '../core/sidebar-state.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';
import { mockViewportMatchMedia } from '../testing/mock-match-media';

describe('NavMenuComponent', () => {
  let fixture: ComponentFixture<NavMenuComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    mockViewportMatchMedia(true);

    await TestBed.configureTestingModule({
      imports: [NavMenuComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavMenuComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('knowly.sidebar.collapsed');
  });

  function flushSessionCheck(loggedIn: boolean): void {
    if (loggedIn) {
      httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });
    } else {
      httpMock
        .expectOne('/api/staff/permissions')
        .flush({}, { status: 401, statusText: 'Unauthorized' });
    }
  }

  function flush(options: {
    memberships?: {
      tenantId: number;
      tenantName: string;
      role: 'MEMBER_ADMIN' | 'MEMBER';
      active: boolean;
    }[];
    globalPermissions?: string[];
    isStaffAccount?: boolean;
    tenantPermissions?: string[] | 'forbidden';
    anyTenantProfileEdit?: boolean;
    // Explicit override for GET /api/tenants/active's response. Undefined derives it from
    // `memberships` (the membership flagged active), null forces the 204 "no active tenant"
    // case regardless of memberships — needed for a staff session (0 memberships) that already
    // has an active tenant via ActiveTenantService.selectTenant()'s optimistic update.
    activeTenant?: {
      tenantId: number;
      tenantName: string;
      role?: 'MEMBER_ADMIN' | 'MEMBER';
    } | null;
  }): void {
    flushSessionCheck(true);
    httpMock.expectOne('/api/staff/permissions').flush({
      permissions: options.globalPermissions ?? [],
      isStaffAccount: options.isStaffAccount ?? false,
    });

    const tenantPermissionsReq = httpMock.expectOne('/api/tenants/permissions');
    if (options.tenantPermissions === 'forbidden' || options.tenantPermissions === undefined) {
      tenantPermissionsReq.flush(
        { code: 'TENANT_ACCESS_DENIED' },
        { status: 403, statusText: 'Forbidden' },
      );
    } else {
      tenantPermissionsReq.flush({ permissions: options.tenantPermissions });
    }

    httpMock
      .expectOne('/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT')
      .flush({ granted: options.anyTenantProfileEdit ?? false });

    // list() (into the local memberships signal) and fetch() (GET /api/tenants/active, into
    // ActiveTenantService's own activeTenantId signal) now hit two different endpoints.
    httpMock
      .match('/api/tenants/memberships')
      .forEach((req) => req.flush(options.memberships ?? []));

    const active =
      options.activeTenant !== undefined
        ? options.activeTenant
        : (() => {
            const activeMembership = (options.memberships ?? []).find((m) => m.active);
            return activeMembership
              ? {
                  tenantId: activeMembership.tenantId,
                  tenantName: activeMembership.tenantName,
                  role: activeMembership.role,
                }
              : null;
          })();
    httpMock.match('/api/tenants/active').forEach((req) => {
      if (req.request.method !== 'GET') {
        return;
      }
      if (active === null) {
        req.flush(null, { status: 204, statusText: 'No Content' });
      } else {
        req.flush(active);
      }
    });
  }

  it('shows nothing when not logged in', () => {
    fixture.detectChanges();
    flushSessionCheck(false);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-menu"]')).toBeFalsy();
  });

  it('only shows links matching the active tenant permissions', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: ['ARTICLE_VIEW'],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-articles"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-members"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-conversations"]')).toBeFalsy();
  });

  it('shows tenant-scoped links for a staff session acting as a tenant (no real membership row)', () => {
    // Staff switching into a tenant never gets a real TenantMembership row — only the
    // permissions endpoint itself (backed by server-side session state) reflects it.
    fixture.detectChanges();
    flush({ memberships: [], tenantPermissions: ['ARTICLE_VIEW', 'DASHBOARD_VIEW'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-articles"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeTruthy();
  });

  it('shows the dashboard link for DASHBOARD_VIEW_GLOBAL alone (staff, no active tenant)', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['DASHBOARD_VIEW_GLOBAL'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeTruthy();
  });

  it('shows the dashboard link for a STAFF_ADMIN-shaped ("all permissions") response even without DASHBOARD_VIEW', () => {
    fixture.detectChanges();
    flush({
      memberships: [],
      globalPermissions: [
        'TENANT_CREATE',
        'TENANT_ACT_AS_ANY',
        'TENANT_MEMBER_MANAGE_ANY',
        'TENANT_ACCESS_GROUP_MANAGE_ANY',
        'TENANT_PERMISSION_GRANT_MANAGE_ANY',
        'STAFF_PERMISSION_MANAGE',
        'STAFF_USER_CREATE',
        'STAFF_USER_VIEW',
        'DASHBOARD_VIEW_GLOBAL',
        'AUDIT_TRAIL_VIEW',
      ],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeTruthy();
  });

  it('shows the access groups link for STAFF_PERMISSION_MANAGE', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['STAFF_PERMISSION_MANAGE'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-access-groups"]')).toBeTruthy();
  });

  it('hides the access groups link without STAFF_PERMISSION_MANAGE', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['STAFF_USER_VIEW'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-access-groups"]')).toBeFalsy();
  });

  it('shows the members link for STAFF_USER_VIEW alone (no tenant permission)', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['STAFF_USER_VIEW'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-members"]')).toBeTruthy();
  });

  it('shows the members link for a STAFF_ADMIN-shaped ("all permissions") response', () => {
    fixture.detectChanges();
    flush({
      memberships: [],
      globalPermissions: [
        'TENANT_CREATE',
        'TENANT_ACT_AS_ANY',
        'TENANT_MEMBER_MANAGE_ANY',
        'TENANT_ACCESS_GROUP_MANAGE_ANY',
        'TENANT_PERMISSION_GRANT_MANAGE_ANY',
        'STAFF_PERMISSION_MANAGE',
        'STAFF_USER_CREATE',
        'STAFF_USER_VIEW',
      ],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-members"]')).toBeTruthy();
  });

  it('hides the members link when neither TENANT_MEMBER_MANAGE nor STAFF_USER_VIEW is held', () => {
    fixture.detectChanges();
    flush({ memberships: [], tenantPermissions: ['ARTICLE_VIEW'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-members"]')).toBeFalsy();
  });

  it('shows Dashboard/Articles/Conversations/Members for a MEMBER_ADMIN, whose /api/tenants/permissions response now includes the full permission set (member-admin-tenant-bypass, backend fix)', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER_ADMIN', active: true }],
      tenantPermissions: ALL_PERMISSIONS,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-articles"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-conversations"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-members"]')).toBeTruthy();
  });

  it('shows the create-tenant link only when granted TENANT_CREATE', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['TENANT_CREATE'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeTruthy();
  });

  it('hides the create-tenant link when granted TENANT_CREATE but already inside a tenant', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({
      memberships: [],
      globalPermissions: ['TENANT_CREATE'],
      activeTenant: { tenantId: 9, tenantName: 'Staffed Co' },
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeFalsy();
  });

  it('shows the switch-tenant link for a 0-membership session holding TENANT_ACT_AS_ANY (REQ-10 first clause)', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['TENANT_ACT_AS_ANY'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeTruthy();
  });

  it('hides the switch-tenant link for a 0-membership session with no TENANT_ACT_AS_ANY and no other GlobalPermissions (REQ-11)', () => {
    fixture.detectChanges();
    flush({ memberships: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeFalsy();
  });

  it('shows the switch-tenant and leave-tenant items for a STAFF account with exactly one real membership (closes the previously-flagged isStaffAccount gap)', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(1, 'Acme').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: [],
      isStaffAccount: true,
      activeTenant: { tenantId: 1, tenantName: 'Acme' },
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeTruthy();
  });

  it('does not show switch-tenant/leave-tenant for a plain MEMBER with exactly one membership (isStaffAccount: false, unchanged regression)', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: [],
      isStaffAccount: false,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeFalsy();
  });

  it('shows the switch-tenant link with more than one membership', () => {
    fixture.detectChanges();
    flush({
      memberships: [
        { tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true },
        { tenantId: 2, tenantName: 'Other Co', role: 'MEMBER', active: false },
      ],
      tenantPermissions: [],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeTruthy();
  });

  it('does not show the switch-tenant link with a single membership', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: [],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeFalsy();
  });

  it('always renders the logo for a logged-in MEMBER with zero tenant permissions and zero memberships-derived state (REQ-7 regression)', () => {
    fixture.detectChanges();
    flush({ memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-brand-wordmark')).toBeTruthy();
  });

  it('never shows "Create tenant" or "leave tenant" for a MEMBER/MEMBER_ADMIN session, regardless of tenant permission level (REQ-12)', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER_ADMIN', active: true }],
      tenantPermissions: ALL_PERMISSIONS,
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeFalsy();
  });

  it('shows "Create tenant" for a STAFF session holding TENANT_CREATE only while not acting inside a tenant session, and hides it once acting inside one (REQ-13)', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['TENANT_CREATE'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeTruthy();

    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});
    fixture.detectChanges();
    // Entering a tenant after session start re-triggers permissionsService.fetch() (the
    // reactive re-fetch effect), same as other post-init selectTenant() tests in this file.
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeFalsy();
  });

  it('no longer shows a "My profile" entry (moved into the avatar dropdown menu)', () => {
    fixture.detectChanges();
    flush({ memberships: [], tenantPermissions: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-my-profile"]')).toBeFalsy();
  });

  it('shows the edit-request inbox link for a PROFILE_EDIT holder in any tenant (not necessarily the active one)', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: ['ARTICLE_VIEW'],
      anyTenantProfileEdit: true,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-profile-edit-requests"]'),
    ).toBeTruthy();
  });

  it('shows the edit-request inbox link for a 0-membership staff session granted PROFILE_EDIT in some tenant', () => {
    fixture.detectChanges();
    flush({ memberships: [], anyTenantProfileEdit: true });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-profile-edit-requests"]'),
    ).toBeTruthy();
  });

  it('shows the edit-request inbox link for a global PROFILE_EDIT holder', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['PROFILE_EDIT'] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-profile-edit-requests"]'),
    ).toBeTruthy();
  });

  it('shows the edit-request inbox link for a tenant ADMIN membership', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER_ADMIN', active: true }],
      tenantPermissions: [],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-profile-edit-requests"]'),
    ).toBeTruthy();
  });

  it('shows the edit-request inbox link for a STAFF_ADMIN-shaped session', () => {
    fixture.detectChanges();
    flush({
      memberships: [],
      globalPermissions: [
        'TENANT_CREATE',
        'TENANT_ACT_AS_ANY',
        'TENANT_MEMBER_MANAGE_ANY',
        'TENANT_ACCESS_GROUP_MANAGE_ANY',
        'TENANT_PERMISSION_GRANT_MANAGE_ANY',
        'STAFF_PERMISSION_MANAGE',
        'STAFF_USER_CREATE',
        'STAFF_USER_VIEW',
        'DASHBOARD_VIEW_GLOBAL',
        'AUDIT_TRAIL_VIEW',
        'PROFILE_EDIT',
      ],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-profile-edit-requests"]'),
    ).toBeTruthy();
  });

  it('hides the edit-request inbox link for a session with none of the above', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: ['ARTICLE_VIEW'],
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="nav-profile-edit-requests"]'),
    ).toBeFalsy();
  });

  it('shows the leave-tenant action for a 0-membership staff session with an active tenant', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({ memberships: [], activeTenant: { tenantId: 9, tenantName: 'Staffed Co' } });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeTruthy();
  });

  it('never shows the leave-tenant action for a session with one or more real memberships', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: [],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeFalsy();
  });

  it('does not show the leave-tenant action for a 0-membership staff session with no active tenant', () => {
    fixture.detectChanges();
    flush({ memberships: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeFalsy();
  });

  it('calls activeTenantService.fetch() during ngOnInit so activeTenantId() does not depend on another routed page having fetched it first', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    const fetchSpy = vi.spyOn(activeTenantService, 'fetch');

    fixture.detectChanges();
    flushSessionCheck(true);
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });
    httpMock
      .expectOne('/api/tenants/permissions')
      .flush({ code: 'TENANT_ACCESS_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock
      .expectOne('/api/tenants/permissions/any-tenant?permission=PROFILE_EDIT')
      .flush({ granted: false });
    httpMock.match('/api/tenants/memberships').forEach((req) => req.flush([]));
    httpMock
      .match('/api/tenants/active')
      .forEach((req) => req.flush(null, { status: 204, statusText: 'No Content' }));
    fixture.detectChanges();

    expect(fetchSpy).toHaveBeenCalled();
  });

  it('clicking leave-tenant calls leaveTenant() and navigates to /welcome on success', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({ memberships: [], activeTenant: { tenantId: 9, tenantName: 'Staffed Co' } });
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="nav-leave-tenant"]',
    ) as HTMLButtonElement;
    button.click();

    httpMock.expectOne('/api/tenants/active/clear').flush({});
    fixture.detectChanges();
    // leaveTenant() changes activeTenantId (9 -> null), which re-triggers
    // permissionsService.fetch() per the reactive re-fetch effect (bug 2 fix).
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });

    expect(navigateSpy).toHaveBeenCalledWith('/welcome');
  });

  it('on a leaveTenant() failure, keeps the active tenant unchanged, shows the error banner, and does not navigate', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({ memberships: [], activeTenant: { tenantId: 9, tenantName: 'Staffed Co' } });
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="nav-leave-tenant"]',
    ) as HTMLButtonElement;
    button.click();

    httpMock
      .expectOne('/api/tenants/active/clear')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(activeTenantService.activeTenantId()).toBe(9);
    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[role="dialog"], [data-testid="confirm-dialog"]'),
    ).toBeFalsy();
  });

  it('never shows a confirmation dialog before or after the leave-tenant click', () => {
    activeTenantServiceSelectAndFlush();

    fixture.detectChanges();
    flush({ memberships: [], activeTenant: { tenantId: 9, tenantName: 'Staffed Co' } });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[role="dialog"], [data-testid="confirm-dialog"]'),
    ).toBeFalsy();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="nav-leave-tenant"]',
    ) as HTMLButtonElement;
    button.click();
    httpMock.expectOne('/api/tenants/active/clear').flush({});
    fixture.detectChanges();
    // leaveTenant() changes activeTenantId (9 -> null), which re-triggers
    // permissionsService.fetch() per the reactive re-fetch effect (bug 2 fix).
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });

    expect(
      fixture.nativeElement.querySelector('[role="dialog"], [data-testid="confirm-dialog"]'),
    ).toBeFalsy();
  });

  function activeTenantServiceSelectAndFlush(): void {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});
  }

  it('hides the leave-tenant action after a successful leave (canLeaveTenant reacts to the signal change)', () => {
    const activeTenantService = TestBed.inject(ActiveTenantService);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    activeTenantService.selectTenant(9, 'Staffed Co').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});

    fixture.detectChanges();
    flush({ memberships: [], activeTenant: { tenantId: 9, tenantName: 'Staffed Co' } });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeTruthy();

    const button = fixture.nativeElement.querySelector(
      '[data-testid="nav-leave-tenant"]',
    ) as HTMLButtonElement;
    button.click();
    httpMock.expectOne('/api/tenants/active/clear').flush({});
    fixture.detectChanges();
    // leaveTenant() changes activeTenantId (9 -> null), which re-triggers
    // permissionsService.fetch() per the reactive re-fetch effect (bug 2 fix).
    httpMock.expectOne('/api/tenants/permissions').flush({ permissions: [] });

    expect(fixture.nativeElement.querySelector('[data-testid="nav-leave-tenant"]')).toBeFalsy();
  });

  it('re-fetches tenant-scoped permissions when entering a tenant after session start, without a full reload', () => {
    fixture.detectChanges();
    flush({ memberships: [] });
    fixture.detectChanges();

    const activeTenantService = TestBed.inject(ActiveTenantService);
    activeTenantService.selectTenant(3, 'Acme').subscribe();
    httpMock.expectOne('/api/tenants/active').flush({});
    fixture.detectChanges();

    // Entering a tenant re-triggers permissionsService.fetch() so overviewGroups() reflects
    // ARTICLE_VIEW/CONVERSATION_USE without requiring a full page reload (bug 2 fix).
    httpMock
      .expectOne('/api/tenants/permissions')
      .flush({ permissions: ['ARTICLE_VIEW', 'CONVERSATION_USE'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-articles"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="nav-conversations"]')).toBeTruthy();
  });

  describe('collapse/expand (desktop rail)', () => {
    it('keeps every data-testid/data-tour-id element present regardless of collapsed()', () => {
      fixture.detectChanges();
      flush({ memberships: [], globalPermissions: ['DASHBOARD_VIEW_GLOBAL'] });
      fixture.detectChanges();

      const sidebarState = TestBed.inject(SidebarStateService);
      expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeTruthy();

      sidebarState.setCollapsed(true);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="nav-dashboard"]')).toBeTruthy();
      expect(fixture.nativeElement.querySelector('[data-tour-id="main-nav"]')).toBeFalsy(); // lives on app-shell, not nav-menu
    });

    it('the collapse/expand toggle button flips collapsed() and its aria-expanded attribute', () => {
      fixture.detectChanges();
      flush({ memberships: [] });
      fixture.detectChanges();

      const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
        '[data-testid="nav-collapse-toggle"]',
      );
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      expect(toggle.getAttribute('aria-controls')).toBe('nav-menu');

      toggle.click();
      fixture.detectChanges();

      expect(TestBed.inject(SidebarStateService).collapsed()).toBe(true);
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
    });

    it('a collapsed item shows a floating tooltip with its label on hover, positioned off the real icon rect', () => {
      fixture.detectChanges();
      flush({ memberships: [], globalPermissions: ['DASHBOARD_VIEW_GLOBAL'] });
      fixture.detectChanges();

      TestBed.inject(SidebarStateService).setCollapsed(true);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="nav-tooltip"]')).toBeNull();

      const link: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="nav-dashboard"]',
      );
      link.dispatchEvent(new Event('mouseenter'));
      fixture.detectChanges();

      const tooltip = fixture.nativeElement.querySelector('[data-testid="nav-tooltip"]');
      expect(tooltip).toBeTruthy();
      expect(tooltip.textContent.trim()).toBe('Dashboard');

      link.dispatchEvent(new Event('mouseleave'));
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="nav-tooltip"]')).toBeNull();
    });

    it('does not show the floating tooltip when the sidebar is expanded', () => {
      fixture.detectChanges();
      flush({ memberships: [], globalPermissions: ['DASHBOARD_VIEW_GLOBAL'] });
      fixture.detectChanges();

      const link: HTMLElement = fixture.nativeElement.querySelector(
        '[data-testid="nav-dashboard"]',
      );
      link.dispatchEvent(new Event('mouseenter'));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="nav-tooltip"]')).toBeNull();
    });
  });
});
