import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { StaffUserDetailPanelComponent } from './staff-user-detail-panel.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('StaffUserDetailPanelComponent', () => {
  let fixture: ComponentFixture<StaffUserDetailPanelComponent>;
  let httpMock: HttpTestingController;

  const emptyDetail = {
    userId: 1,
    email: 'staffer@example.com',
    directPermissions: [],
    accessGroups: [],
    effectivePermissions: [],
  };

  async function createFixture(viewerIsStaffAdmin: boolean): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [StaffUserDetailPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(StaffUserDetailPanelComponent);
    fixture.componentRef.setInput('userId', 1);
    fixture.componentRef.setInput('viewerIsStaffAdmin', viewerIsStaffAdmin);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  function flushAuditTrail(events: unknown[] = []): void {
    httpMock.expectOne('/api/staff/users/1/audit-trail').flush(events);
  }

  function flushProfile(): void {
    httpMock.expectOne('/api/users/1/profile').flush({
      userId: 1,
      email: 'staffer@example.com',
      fullName: 'Staffer',
      address: '123 Main St',
      rg: '11.111.111-1',
      cpf: '111.111.111-11',
      phone: '+15550000',
    });
  }

  it('renders direct permissions, access groups, and effective permissions as distinct sections', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush({
      ...emptyDetail,
      directPermissions: ['STAFF_USER_CREATE'],
      accessGroups: [{ id: 5, name: 'Support' }],
      effectivePermissions: ['STAFF_USER_CREATE'],
    });
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-direct-permissions"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-access-groups"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-effective-permissions"]'),
    ).toBeTruthy();
  });

  it('fetches and renders the audit trail reverse-chronological, alongside the other sections', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    flushAuditTrail([
      {
        occurredAt: '2026-07-20T10:00:00Z',
        action: 'GRANT_PERMISSION',
        resourceType: 'STAFF_PERMISSION',
        resourceId: 'STAFF_USER_CREATE',
        tenantId: null,
        outcome: 'SUCCESS',
        metadata: {},
      },
      {
        occurredAt: '2026-07-19T10:00:00Z',
        action: 'LOGIN',
        resourceType: 'SESSION',
        resourceId: '42',
        tenantId: '7',
        outcome: 'SUCCESS',
        metadata: {},
      },
    ]);
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const section = fixture.nativeElement.querySelector('[data-testid="staff-audit-trail"]');
    expect(section).toBeTruthy();

    const rows = section.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('GRANT_PERMISSION');
    expect(rows[0].textContent).toContain('global');
    expect(rows[1].textContent).toContain('LOGIN');
    expect(rows[1].textContent).toContain('7');
  });

  it('renders a permission-denied state only inside the audit-trail section on a 403, other sections unaffected', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    httpMock
      .expectOne('/api/staff/users/1/audit-trail')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const section = fixture.nativeElement.querySelector('[data-testid="staff-audit-trail"]');
    expect(section.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-direct-permissions"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-access-groups"]')).toBeTruthy();
  });

  it('renders a distinct "no audit history" message when there are zero events', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    flushAuditTrail([]);
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const section = fixture.nativeElement.querySelector('[data-testid="staff-audit-trail"]');
    expect(section.querySelector('[data-testid="staff-audit-trail-empty"]')).toBeTruthy();
    expect(section.querySelectorAll('tbody tr').length).toBe(0);
  });

  it('toggling a permission calls grant/revoke and re-fetches the detail', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const toggle: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-permission-toggle-STAFF_USER_CREATE"]',
    );
    toggle.dispatchEvent(new Event('click'));

    const grantReq = httpMock.expectOne('/api/staff/users/1/permissions');
    expect(grantReq.request.method).toBe('POST');
    expect(grantReq.request.body).toEqual({ permission: 'STAFF_USER_CREATE' });
    grantReq.flush({});

    httpMock.expectOne('/api/staff/users/1/permissions').flush({
      ...emptyDetail,
      directPermissions: ['STAFF_USER_CREATE'],
      effectivePermissions: ['STAFF_USER_CREATE'],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('STAFF_USER_CREATE');
  });

  it('creating a global access group makes it available to assign', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-new-access-group-name"]',
    );
    nameInput.value = 'Support';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-new-access-group-form"]',
    );
    form.dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/staff/access-groups').flush({ id: 5, name: 'Support' });
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-assign-access-group-5"]'),
    ).toBeTruthy();
  });

  it('assigning and unassigning an access group updates the staff user shown state', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const assignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-assign-access-group-5"]',
    );
    assignButton.click();

    httpMock.expectOne('/api/staff/users/1/access-groups/5').flush({});
    httpMock
      .expectOne('/api/staff/users/1/permissions')
      .flush({ ...emptyDetail, accessGroups: [{ id: 5, name: 'Support' }] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-unassign-access-group-5"]'),
    ).toBeTruthy();

    const unassignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-unassign-access-group-5"]',
    );
    unassignButton.click();

    httpMock.expectOne('/api/staff/users/1/access-groups/5').flush({});
    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-unassign-access-group-5"]'),
    ).toBeFalsy();
  });

  it('disables every management control when viewerIsStaffAdmin is false', async () => {
    await createFixture(false);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/1/permissions')
      .flush({ ...emptyDetail, accessGroups: [{ id: 5, name: 'Support' }] });
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const toggle: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-permission-toggle-STAFF_USER_CREATE"]',
    );
    expect(toggle.disabled).toBe(true);

    const unassignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-unassign-access-group-5"]',
    );
    expect(unassignButton.disabled).toBe(true);

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-new-access-group-form"]'),
    ).toBeFalsy();
  });

  it('enables every management control when viewerIsStaffAdmin is true', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/1/permissions')
      .flush({ ...emptyDetail, accessGroups: [{ id: 5, name: 'Support' }] });
    httpMock.expectOne('/api/staff/access-groups').flush([{ id: 5, name: 'Support' }]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    const toggle: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-permission-toggle-STAFF_USER_CREATE"]',
    );
    expect(toggle.disabled).toBe(false);

    const unassignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-unassign-access-group-5"]',
    );
    expect(unassignButton.disabled).toBe(false);

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-new-access-group-form"]'),
    ).toBeTruthy();
  });

  it('renders a permission-denied state on a 403 from any panel action', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/1/permissions')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    flushAuditTrail();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('renders the profile section alongside its other, untouched sections', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    flushAuditTrail();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-section"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-effective-permissions"]'),
    ).toBeTruthy();
  });
});
