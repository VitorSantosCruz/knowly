import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { StaffDirectoryPageComponent } from './staff-directory-page.component';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('StaffDirectoryPageComponent', () => {
  let fixture: ComponentFixture<StaffDirectoryPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StaffDirectoryPageComponent],
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

    fixture = TestBed.createComponent(StaffDirectoryPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders the staff user list via app-shared-list once it resolves', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 1, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-shared-list')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('staffer@example.com');
    expect(fixture.nativeElement.textContent).toContain('Staff');
  });

  it('entering a search term calls list(email) and refreshes the list', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users').flush([]);
    fixture.detectChanges();

    const searchInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-search-email"]',
    );
    searchInput.value = 'bob';
    searchInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users?email=bob')
      .flush([{ id: 2, email: 'bob@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('bob@example.com');
  });

  it('submitting the create form collects the mandatory profile first, then posts it and refreshes the list', () => {
    const permissionsService = TestBed.inject(GlobalPermissionsService);
    permissionsService.fetch();
    httpMock.expectOne('/api/staff/permissions').flush({ permissions: ['STAFF_USER_CREATE'] });

    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users').flush([]);
    fixture.detectChanges();

    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="create-staff-user-email"]',
    );
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="create-staff-user-form"]',
    );
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    // The plain email form is replaced by the mandatory-profile-fields form (backend requires
    // a full profile on this request) rather than posting immediately.
    expect(
      fixture.nativeElement.querySelector('[data-testid="create-staff-user-form"]'),
    ).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="create-staff-user-profile-form"]'),
    ).toBeTruthy();

    const input = (testId: string): HTMLInputElement =>
      fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

    input('profile-field-fullName').value = 'New Staffer';
    input('profile-field-fullName').dispatchEvent(new Event('input'));
    input('profile-field-countryCode').value = 'BR';
    input('profile-field-countryCode').dispatchEvent(new Event('change'));
    input('profile-field-taxId').value = '111.111.111-11';
    input('profile-field-taxId').dispatchEvent(new Event('input'));
    input('profile-address-field-addressLine1').value = 'Main St, 123';
    input('profile-address-field-addressLine1').dispatchEvent(new Event('input'));
    input('profile-address-field-city').value = 'Sao Paulo';
    input('profile-address-field-city').dispatchEvent(new Event('input'));
    input('profile-address-field-stateRegion').value = 'SP';
    input('profile-address-field-stateRegion').dispatchEvent(new Event('input'));
    input('profile-address-field-postalCode').value = '01000-000';
    input('profile-address-field-postalCode').dispatchEvent(new Event('input'));
    fixture.detectChanges();

    input('profile-contact-add').click();
    fixture.detectChanges();
    const newRow = fixture.nativeElement.querySelector('[data-testid^="profile-contact-row-"]');
    const rowKey = newRow.getAttribute('data-testid').replace('profile-contact-row-', '');
    input(`profile-contact-type-${rowKey}`).value = 'EMAIL';
    input(`profile-contact-type-${rowKey}`).dispatchEvent(new Event('change'));
    fixture.detectChanges();
    input(`profile-contact-value-${rowKey}`).value = 'new@example.com';
    input(`profile-contact-value-${rowKey}`).dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/staff/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.email).toBe('new@example.com');
    expect(req.request.body.profile.fullName).toBe('New Staffer');
    req.flush({});
    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 3, email: 'new@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('new@example.com');
  });

  it('hides the create form when the viewer is neither staff-admin nor STAFF_USER_CREATE-holding', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/staff/users').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="create-staff-user-form"]'),
    ).toBeFalsy();
  });

  it('shows a permission-denied state on a 403 from the list', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/staff/users')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('selecting a staff user shows the detail panel', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/staff/users')
      .flush([{ id: 1, email: 'staffer@example.com', globalRole: 'STAFF' }]);
    fixture.detectChanges();

    const editButton: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-sharedList.actions.edit-1"]',
    );
    editButton.click();
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush({
      userId: 1,
      email: 'staffer@example.com',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    httpMock.expectOne('/api/staff/users/1/audit-trail').flush([]);
    fixture.detectChanges();
    httpMock.expectOne('/api/users/1/profile').flush({
      userId: 1,
      email: 'staffer@example.com',
      fields: {
        fullName: 'Staffer',
        cpf: '111.111.111-11',
        rg: '11.111.111-1',
        rgOrgaoEmissor: 'SSP',
        birthDate: '1990-01-01',
        address: null,
        contacts: [],
      },
      avatarUrl: null,
    });
    httpMock.expectOne('/api/users/me/profile').flush({
      userId: 999,
      email: 'me@example.com',
      fields: {
        fullName: 'Me',
        cpf: null,
        rg: null,
        rgOrgaoEmissor: null,
        birthDate: null,
        address: null,
        contacts: [],
      },
      avatarUrl: null,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-user-detail-panel"]'),
    ).toBeTruthy();
  });
});
