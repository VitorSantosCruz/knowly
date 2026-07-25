import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { NavMenuComponent } from './nav-menu.component';
import { AuthService } from '../core/auth.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('NavMenuComponent', () => {
  let fixture: ComponentFixture<NavMenuComponent>;
  let httpMock: HttpTestingController;
  let authService: AuthService;

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
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flush(options: {
    memberships?: {
      tenantId: number;
      tenantName: string;
      role: 'ADMIN' | 'MEMBER';
      active: boolean;
    }[];
    globalPermissions?: string[];
    tenantPermissions?: string[];
  }): void {
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: options.globalPermissions ?? [] });
    httpMock.expectOne('/api/tenants/memberships').flush(options.memberships ?? []);

    if (options.tenantPermissions) {
      httpMock
        .expectOne('/api/tenants/permissions')
        .flush({ permissions: options.tenantPermissions });
    }
  }

  it('shows nothing and fetches nothing when not logged in', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-menu"]')).toBeFalsy();
    httpMock.expectNone('/api/staff/permissions');
    httpMock.expectNone('/api/tenants/memberships');
  });

  it('only shows links matching the active tenant permissions', () => {
    vi.spyOn(authService, 'isLoggedIn').mockReturnValue(true);

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

  it('shows the create-tenant link only when granted TENANT_CREATE', () => {
    vi.spyOn(authService, 'isLoggedIn').mockReturnValue(true);

    fixture.detectChanges();
    flush({ memberships: [], globalPermissions: ['TENANT_CREATE'] });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-create-tenant"]')).toBeTruthy();
  });

  it('does not fetch tenant permissions when there is no active membership', () => {
    vi.spyOn(authService, 'isLoggedIn').mockReturnValue(true);

    fixture.detectChanges();
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [] });
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants/permissions');
  });

  it('shows the switch-tenant link only with more than one membership', () => {
    vi.spyOn(authService, 'isLoggedIn').mockReturnValue(true);

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
    vi.spyOn(authService, 'isLoggedIn').mockReturnValue(true);

    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
      tenantPermissions: [],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="nav-switch-tenant"]')).toBeFalsy();
  });
});
