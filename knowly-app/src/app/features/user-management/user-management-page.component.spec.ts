import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { UserManagementPageComponent } from './user-management-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('UserManagementPageComponent', () => {
  let fixture: ComponentFixture<UserManagementPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserManagementPageComponent],
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

    fixture = TestBed.createComponent(UserManagementPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows a loading state while the active tenant has not resolved', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-members-page')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('app-staff-directory-page')).toBeFalsy();

    httpMock.expectOne('/api/tenants/memberships').flush([]);
  });

  it('renders MembersPageComponent when an active tenant is resolved', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 7, tenantName: 'Acme', role: 'ADMIN', active: true }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-members-page')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-staff-directory-page')).toBeFalsy();

    // MembersPageComponent (unchanged, reused as-is) calls ActiveTenantService.fetch()
    // itself in its own ngOnInit, independent of the wrapper's own fetch() above.
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 7, tenantName: 'Acme', role: 'ADMIN', active: true }]);
    httpMock.expectOne('/api/tenants/7/members').flush([]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
  });

  it('renders StaffDirectoryPageComponent when no active tenant is resolved (staff)', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-staff-directory-page')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-members-page')).toBeFalsy();

    httpMock.expectOne('/api/staff/users').flush([]);
  });
});
