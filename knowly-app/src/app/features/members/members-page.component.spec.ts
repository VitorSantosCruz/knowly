import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { MembersPageComponent } from './members-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('MembersPageComponent', () => {
  let fixture: ComponentFixture<MembersPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MembersPageComponent],
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

    fixture = TestBed.createComponent(MembersPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushActiveTenant() {
    httpMock
      .expectOne('/api/tenants/active')
      .flush({ tenantId: 7, tenantName: 'Acme', role: 'MEMBER_ADMIN' });
  }

  it('renders the member list once the active tenant and members resolve', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-shared-list')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('a@example.com');
  });

  it('adding a member collects the mandatory profile first, then posts it and refreshes the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/7/members').flush([]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const emailInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="add-member-email"]',
    );
    emailInput.value = 'new@example.com';
    emailInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="add-member-form"]',
    );
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    // The plain email/role form is replaced by the mandatory-profile-fields form (backend
    // requires a full profile on this request) rather than posting immediately.
    expect(fixture.nativeElement.querySelector('[data-testid="add-member-form"]')).toBeFalsy();
    const profileForm = fixture.nativeElement.querySelector(
      '[data-testid="add-member-profile-form"]',
    );
    expect(profileForm).toBeTruthy();

    const input = (testId: string): HTMLInputElement =>
      fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

    input('profile-field-fullName').value = 'New Member';
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

    const req = httpMock.expectOne('/api/tenants/7/members');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.email).toBe('new@example.com');
    expect(req.request.body.role).toBe('MEMBER');
    expect(req.request.body.profile.fullName).toBe('New Member');
    req.flush({});
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 2, email: 'new@example.com', role: 'MEMBER' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('new@example.com');
  });

  it('removing a member removes it from the list', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const removeButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-sharedList.actions.delete-1"]',
    );
    removeButton.click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/api/tenants/7/members/1');
    expect(deleteReq.request.body).toEqual({ word: 'correct-horse' });
    deleteReq.flush({});
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('a@example.com');
  });

  it('selecting a member shows the detail panel', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush([{ membershipId: 1, userId: 42, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    const editButton: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="shared-list-action-sharedList.actions.edit-1"]',
    );
    editButton.click();
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/7/members/1').flush({
      membershipId: 1,
      userId: 42,
      email: 'a@example.com',
      role: 'MEMBER',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush({
      userId: 42,
      email: 'a@example.com',
      fields: {
        fullName: 'Member A',
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

    expect(fixture.nativeElement.querySelector('[data-testid="member-detail-panel"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="profile-section"]')).toBeTruthy();
  });

  it('shows a permission-denied state when the members list is forbidden', () => {
    fixture.detectChanges();
    flushActiveTenant();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/7/members')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
