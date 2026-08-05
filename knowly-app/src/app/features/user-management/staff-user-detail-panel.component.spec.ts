import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { StaffUserDetailPanelComponent } from './staff-user-detail-panel.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { GlobalPermission } from '../../core/global-permission';

describe('StaffUserDetailPanelComponent', () => {
  let fixture: ComponentFixture<StaffUserDetailPanelComponent>;
  let httpMock: HttpTestingController;

  const staffDetail = {
    userId: 1,
    email: 'staffer@example.com',
    globalRole: 'STAFF' as const,
    directPermissions: [] as GlobalPermission[],
    accessGroups: [] as { id: number; name: string }[],
    effectivePermissions: [] as GlobalPermission[],
    isLastAdminOfType: false,
  };

  const adminDetail = {
    ...staffDetail,
    globalRole: 'STAFF_ADMIN' as const,
    isLastAdminOfType: false,
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

  const profileFields = {
    fullName: 'Staffer',
    cpf: '111.111.111-11',
    rg: '11.111.111-1',
    rgOrgaoEmissor: 'SSP',
    birthDate: '1990-01-01',
    address: null,
    contacts: [],
  };

  function flushProfile(): void {
    httpMock.expectOne('/api/users/1/profile').flush({
      userId: 1,
      email: 'staffer@example.com',
      fields: profileFields,
      avatarUrl: null,
    });
  }

  function flushOwnProfile(ownUserId = 999): void {
    httpMock.expectOne('/api/users/me/profile').flush({
      userId: ownUserId,
      email: 'me@example.com',
      fields: profileFields,
      avatarUrl: null,
    });
  }

  async function open(
    detail: typeof staffDetail | typeof adminDetail,
    viewerIsStaffAdmin: boolean,
    accessGroups: unknown[] = [],
  ): Promise<void> {
    await createFixture(viewerIsStaffAdmin);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(detail);
    httpMock.expectOne('/api/staff/access-groups').flush(accessGroups);
    fixture.detectChanges();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();
  }

  it('renders "Editar perfil" in the top header, before the permission sections', async () => {
    await open(staffDetail, true);

    const panel = fixture.nativeElement.querySelector('[data-testid="staff-user-detail-panel"]');
    const header = panel.querySelector('header');
    expect(header.querySelector('[data-testid="staff-edit-profile-button"]')).toBeTruthy();

    const children = Array.from(panel.children) as HTMLElement[];
    expect(children.indexOf(header)).toBe(0);
  });

  it('no longer embeds the (unpaginated) audit-trail table (replaced by the /staff/users/:userId/audit route, REQ-8)', async () => {
    await open(staffDetail, true);

    expect(fixture.nativeElement.querySelector('[data-testid="staff-audit-trail"]')).toBeFalsy();
  });

  it('openInEditMode() puts the embedded profile section into edit mode', async () => {
    await open(staffDetail, true);

    expect(fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]')).toBeFalsy();

    fixture.componentInstance.openInEditMode();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]')).toBeTruthy();
  });

  it('clicking "Editar perfil" opens the profile edit form', async () => {
    await open(staffDetail, true);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-edit-profile-button"]',
    );
    button.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-profile-fields-form')).toBeTruthy();
  });

  it('shows no permission switches for a STAFF_ADMIN target, only a demote action, gated by viewer role', async () => {
    await open(adminDetail, true);

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-direct-permissions"]'),
    ).toBeFalsy();
    expect(fixture.nativeElement.querySelector('[data-testid="staff-demote-button"]')).toBeTruthy();
  });

  it('hides the demote action entirely when the viewer is not a STAFF_ADMIN', async () => {
    await open(adminDetail, false);

    expect(fixture.nativeElement.querySelector('[data-testid="staff-demote-button"]')).toBeFalsy();
  });

  it('disables demote with an explanation when the target is the last STAFF_ADMIN', async () => {
    await open({ ...adminDetail, isLastAdminOfType: true }, true);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-demote-button"]',
    );
    expect(button.disabled).toBe(true);
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-demote-disabled-reason"]'),
    ).toBeTruthy();
  });

  it('confirming demote calls the demote endpoint and refreshes the detail', async () => {
    await open(adminDetail, true);

    fixture.nativeElement.querySelector('[data-testid="staff-demote-button"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="staff-demote-confirm"]').click();

    const req = httpMock.expectOne('/api/staff/users/1/demote');
    expect(req.request.method).toBe('POST');
    req.flush({});

    httpMock.expectOne('/api/staff/users/1/permissions').flush(staffDetail);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-direct-permissions"]'),
    ).toBeTruthy();
  });

  it('shows a "promote to STAFF_ADMIN" action for a STAFF target, gated by viewer role, never disabled', async () => {
    await open(staffDetail, true);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-promote-button"]',
    );
    expect(button).toBeTruthy();
    expect(button.disabled).toBe(false);
  });

  it('hides the promote action when the viewer is not a STAFF_ADMIN', async () => {
    await open(staffDetail, false);

    expect(fixture.nativeElement.querySelector('[data-testid="staff-promote-button"]')).toBeFalsy();
  });

  it('confirming promote calls the promote endpoint and refreshes the detail', async () => {
    await open(staffDetail, true);

    fixture.nativeElement.querySelector('[data-testid="staff-promote-button"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="staff-promote-confirm"]').click();

    const req = httpMock.expectOne('/api/staff/users/1/promote');
    expect(req.request.method).toBe('POST');
    req.flush({});

    httpMock.expectOne('/api/staff/users/1/permissions').flush(adminDetail);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="staff-demote-button"]')).toBeTruthy();
  });

  it('renders switches (not checkboxes) for a STAFF target, seeded from directPermissions, toggling only local state', async () => {
    await open({ ...staffDetail, directPermissions: ['STAFF_USER_CREATE'] }, true);

    const toggle = fixture.nativeElement.querySelector(
      '[data-testid="staff-permission-toggle-STAFF_USER_CREATE"]',
    );
    expect(toggle.getAttribute('role')).toBe('switch');
    expect(toggle.getAttribute('aria-checked')).toBe('true');

    toggle.click();
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-checked')).toBe('false');
    // No HTTP call fired per toggle.
    httpMock.expectNone('/api/staff/users/1/permissions');
  });

  it('hides "Save" with zero pending changes and shows it once a switch is toggled', async () => {
    await open(staffDetail, true);

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-save-permissions-button"]'),
    ).toBeFalsy();

    fixture.nativeElement
      .querySelector('[data-testid="staff-permission-toggle-STAFF_USER_CREATE"]')
      .click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-save-permissions-button"]'),
    ).toBeTruthy();
  });

  it('clicking Save opens one confirm dialog and submits the full pending set on confirm', async () => {
    await open(staffDetail, true);

    fixture.nativeElement
      .querySelector('[data-testid="staff-permission-toggle-STAFF_USER_CREATE"]')
      .click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="staff-save-permissions-button"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/1/permissions/batch/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();

    const req = httpMock.expectOne('/api/staff/users/1/permissions/batch');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      permissions: ['STAFF_USER_CREATE'],
      word: 'correct-horse',
    });
    req.flush({});

    httpMock.expectOne('/api/staff/users/1/permissions').flush(staffDetail);
  });

  it('renders permission names via the translation map, not the raw enum', async () => {
    await open({ ...staffDetail, effectivePermissions: ['STAFF_USER_CREATE'] }, true);

    const section = fixture.nativeElement.querySelector(
      '[data-testid="staff-effective-permissions"]',
    );
    expect(section.textContent).not.toContain('STAFF_USER_CREATE');
  });

  it('assigning and unassigning an access group updates the staff user shown state', async () => {
    await open(staffDetail, true, [{ id: 5, name: 'Support' }]);

    const assignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-assign-access-group-5"]',
    );
    assignButton.click();

    // Real backend returns ResponseEntity.ok().build() -- a genuinely empty body, which
    // Angular parses as `null`, not `{}`.
    httpMock.expectOne('/api/staff/users/1/access-groups/5').flush(null);
    httpMock
      .expectOne('/api/staff/users/1/permissions')
      .flush({ ...staffDetail, accessGroups: [{ id: 5, name: 'Support' }] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-unassign-access-group-5"]'),
    ).toBeTruthy();

    const unassignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="staff-unassign-access-group-5"]',
    );
    unassignButton.click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/1/access-groups/5/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();
    fixture.detectChanges();

    const deleteReq = httpMock.expectOne('/api/staff/users/1/access-groups/5');
    expect(deleteReq.request.body).toEqual({ word: 'correct-horse' });
    deleteReq.flush({});
    httpMock.expectOne('/api/staff/users/1/permissions').flush(staffDetail);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-unassign-access-group-5"]'),
    ).toBeFalsy();
  });

  it('does not offer inline access-group creation from this panel', async () => {
    await open(staffDetail, true);

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-new-access-group-form"]'),
    ).toBeFalsy();
  });

  it('renders a permission-denied state on a 403 from the detail fetch', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/users/1/permissions')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    httpMock.expectOne('/api/users/me/profile').flush(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('threads ownUserId into ProfileSectionComponent, hiding self-edit', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/users/1/permissions').flush(staffDetail);
    httpMock.expectOne('/api/staff/access-groups').flush([]);
    fixture.detectChanges();
    flushProfile();
    flushOwnProfile(1);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-edit-profile-button"]'),
    ).toBeFalsy();
  });
});
