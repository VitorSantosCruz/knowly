import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { NavMenuComponent } from './nav-menu.component';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('NavMenuComponent', () => {
  let fixture: ComponentFixture<NavMenuComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
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
      role: 'ADMIN' | 'MEMBER';
      active: boolean;
    }[];
    globalPermissions?: string[];
    tenantPermissions?: string[] | 'forbidden';
  }): void {
    flushSessionCheck(true);
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: options.globalPermissions ?? [] });

    const tenantPermissionsReq = httpMock.expectOne('/api/tenants/permissions');
    if (options.tenantPermissions === 'forbidden' || options.tenantPermissions === undefined) {
      tenantPermissionsReq.flush(
        { code: 'TENANT_ACCESS_DENIED' },
        { status: 403, statusText: 'Forbidden' },
      );
    } else {
      tenantPermissionsReq.flush({ permissions: options.tenantPermissions });
    }

    httpMock.expectOne('/api/tenants/memberships').flush(options.memberships ?? []);
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

  it('shows the create-tenant link only when granted TENANT_CREATE', () => {
    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['TENANT_CREATE'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeTruthy();
  });

  it('shows the switch-tenant link for a 0-membership (staff) session', () => {
    fixture.detectChanges();
    flush({ memberships: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeTruthy();
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
});
