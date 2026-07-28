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
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-direct-permissions"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-access-groups"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-effective-permissions"]'),
    ).toBeTruthy();
  });

  it('toggling a permission calls grant/revoke and re-fetches the detail', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(emptyDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
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
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });
});
