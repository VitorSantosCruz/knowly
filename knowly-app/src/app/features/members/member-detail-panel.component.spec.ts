import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { MemberDetailPanelComponent } from './member-detail-panel.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { Permission } from '../../core/permission';
import { GlobalPermissionsService } from '../../core/global-permissions.service';

describe('MemberDetailPanelComponent', () => {
  let fixture: ComponentFixture<MemberDetailPanelComponent>;
  let httpMock: HttpTestingController;

  const memberDetail = {
    membershipId: 2,
    userId: 1,
    email: 'member@example.com',
    role: 'MEMBER' as const,
    directPermissions: [] as Permission[],
    accessGroups: [] as { id: number; name: string }[],
    effectivePermissions: [] as Permission[],
    isLastAdminOfType: false,
  };

  const adminDetail = {
    ...memberDetail,
    role: 'MEMBER_ADMIN' as const,
    isLastAdminOfType: false,
  };

  async function createFixture(viewerIsMemberAdmin: boolean): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [MemberDetailPanelComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MemberDetailPanelComponent);
    fixture.componentRef.setInput('tenantId', 1);
    fixture.componentRef.setInput('membershipId', 2);
    fixture.componentRef.setInput('viewerIsMemberAdminOfThisTenant', viewerIsMemberAdmin);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  const profileFields = {
    fullName: 'Member',
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
      email: 'member@example.com',
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
    detail: typeof memberDetail | typeof adminDetail,
    viewerIsMemberAdmin: boolean,
    accessGroups: unknown[] = [],
  ): Promise<void> {
    await createFixture(viewerIsMemberAdmin);
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/1/members/2').flush(detail);
    httpMock.expectOne('/api/tenants/1/access-groups').flush(accessGroups);
    fixture.detectChanges();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();
  }

  function selectPermissionsTab(): void {
    fixture.nativeElement.querySelector('[data-testid="member-tab-permissions"]').click();
    fixture.detectChanges();
  }

  // Switching back to "Personal data" remounts `app-profile-section` (a fresh structural `@if`
  // instance), which re-fetches the target's own profile data on `ngOnChanges` -- flush that here
  // so callers don't each need to know about it. `ownUserId` is loaded once by the panel itself
  // (not by `app-profile-section`), so it is not re-fetched on tab switches.
  function selectPersonalTab(): void {
    fixture.nativeElement.querySelector('[data-testid="member-tab-personal"]').click();
    fixture.detectChanges();
    flushProfile();
    fixture.detectChanges();
  }

  it('renders "Editar perfil" in the top header, before the audit/permission sections', async () => {
    await open(memberDetail, true);

    const panel = fixture.nativeElement.querySelector('[data-testid="member-detail-panel"]');
    const header = panel.querySelector('header');
    expect(header.querySelector('[data-testid="member-edit-profile-button"]')).toBeTruthy();

    const children = Array.from(panel.children) as HTMLElement[];
    expect(children.indexOf(header)).toBe(0);
  });

  it('renders "Personal data"/"Permissions" tabs in order, defaulting to Personal data', async () => {
    await open(memberDetail, true);

    const tablist = fixture.nativeElement.querySelector('[role="tablist"]');
    expect(tablist).toBeTruthy();
    const tabs: HTMLElement[] = Array.from(tablist.querySelectorAll('[role="tab"]'));
    expect(tabs.map((t) => t.getAttribute('data-testid'))).toEqual([
      'member-tab-personal',
      'member-tab-permissions',
    ]);
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[1].getAttribute('aria-selected')).toBe('false');

    expect(fixture.nativeElement.querySelector('[data-testid="access-groups"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="direct-permissions"]')).toBeFalsy();
  });

  it('clicking the Permissions tab shows permission content and hides personal-data content, and vice versa', async () => {
    await open({ ...memberDetail, directPermissions: ['ARTICLE_CREATE'] }, true);

    selectPermissionsTab();

    expect(fixture.nativeElement.querySelector('[data-testid="direct-permissions"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="access-groups"]')).toBeFalsy();

    selectPersonalTab();

    expect(fixture.nativeElement.querySelector('[data-testid="access-groups"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="direct-permissions"]')).toBeFalsy();
  });

  it('shows no permission switches for a MEMBER_ADMIN target, only a demote action, gated by viewer role', async () => {
    await open(adminDetail, true);

    selectPermissionsTab();
    expect(fixture.nativeElement.querySelector('[data-testid="direct-permissions"]')).toBeFalsy();

    selectPersonalTab();
    expect(
      fixture.nativeElement.querySelector('[data-testid="member-demote-button"]'),
    ).toBeTruthy();
  });

  it('hides the demote action when the viewer is not a MEMBER_ADMIN of this tenant', async () => {
    await open(adminDetail, false);

    expect(fixture.nativeElement.querySelector('[data-testid="member-demote-button"]')).toBeFalsy();
  });

  it('disables demote with an explanation when the target is the last MEMBER_ADMIN of the tenant', async () => {
    await open({ ...adminDetail, isLastAdminOfType: true }, true);

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="member-demote-button"]',
    );
    expect(button.disabled).toBe(true);
    expect(
      fixture.nativeElement.querySelector('[data-testid="member-demote-disabled-reason"]'),
    ).toBeTruthy();
  });

  it('confirming demote calls the demote endpoint and refreshes the detail', async () => {
    await open(adminDetail, true);

    fixture.nativeElement.querySelector('[data-testid="member-demote-button"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="member-demote-confirm"]').click();

    const req = httpMock.expectOne('/api/tenants/1/members/2/demote');
    expect(req.request.method).toBe('POST');
    req.flush({});

    httpMock.expectOne('/api/tenants/1/members/2').flush(memberDetail);
    fixture.detectChanges();

    selectPermissionsTab();
    expect(fixture.nativeElement.querySelector('[data-testid="direct-permissions"]')).toBeTruthy();
  });

  it('shows a "promote to MEMBER_ADMIN" action for a MEMBER target, gated by viewer role, never disabled', async () => {
    await open(memberDetail, true);
    selectPermissionsTab();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="member-promote-button"]',
    );
    expect(button).toBeTruthy();
    expect(button.disabled).toBe(false);
  });

  it('hides the promote action when the viewer is not a MEMBER_ADMIN of this tenant', async () => {
    await open(memberDetail, false);
    selectPermissionsTab();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-promote-button"]'),
    ).toBeFalsy();
  });

  it('confirming promote calls the promote endpoint and refreshes the detail', async () => {
    await open(memberDetail, true);
    selectPermissionsTab();

    fixture.nativeElement.querySelector('[data-testid="member-promote-button"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="member-promote-confirm"]').click();

    const req = httpMock.expectOne('/api/tenants/1/members/2/promote');
    expect(req.request.method).toBe('POST');
    req.flush({});

    httpMock.expectOne('/api/tenants/1/members/2').flush(adminDetail);
    fixture.detectChanges();

    selectPersonalTab();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-demote-button"]'),
    ).toBeTruthy();
  });

  it('no longer offers a delete affordance inside the panel (moved to the list row action, REQ-7)', async () => {
    await open({ ...adminDetail, isLastAdminOfType: true }, true);

    expect(fixture.nativeElement.querySelector('[data-testid="member-delete-button"]')).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="member-delete-disabled-reason"]'),
    ).toBeFalsy();
    const instance = fixture.componentInstance as unknown as Record<string, unknown>;
    expect(instance['pendingDelete']).toBeUndefined();
    expect(instance['deleteRetryToken']).toBeUndefined();
    expect(instance['deletionTokenFetcher']).toBeUndefined();
    expect(instance['confirmDelete']).toBeUndefined();
    expect(instance['cancelDelete']).toBeUndefined();
  });

  it('openInEditMode() puts the embedded profile section into edit mode', async () => {
    await open(memberDetail, true);

    expect(fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]')).toBeFalsy();

    fixture.componentInstance.openInEditMode();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-fields-form"]')).toBeTruthy();
  });

  it('the old inline toggle-grid markup is gone, replaced by app-permission-list', async () => {
    await open({ ...memberDetail, directPermissions: ['ARTICLE_CREATE'] }, true);
    selectPermissionsTab();

    expect(
      fixture.nativeElement.querySelector('[data-testid="permission-toggle-ARTICLE_CREATE"]'),
    ).toBeFalsy();
    expect(fixture.nativeElement.querySelector('app-permission-list')).toBeTruthy();
  });

  it('renders switches for a MEMBER target, seeded from directPermissions, toggling only local state', async () => {
    await open({ ...memberDetail, directPermissions: ['ARTICLE_CREATE'] }, true);
    selectPermissionsTab();

    const toggle = fixture.nativeElement.querySelector(
      '[data-testid="permission-list-toggle-ARTICLE_CREATE"]',
    );
    expect(toggle.getAttribute('role')).toBe('switch');
    expect(toggle.getAttribute('aria-checked')).toBe('true');

    toggle.click();
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-checked')).toBe('false');
    httpMock.expectNone('/api/tenants/1/members/2');
  });

  it('disables permission switches for a viewer who is neither tenant admin nor globally permitted', async () => {
    await open({ ...memberDetail, directPermissions: ['ARTICLE_CREATE'] }, false);
    selectPermissionsTab();

    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="permission-list-toggle-ARTICLE_CREATE"]',
    );
    expect(toggle.disabled).toBe(true);
  });

  it('enables permission switches for a staff viewer holding TENANT_PERMISSION_GRANT_CREATE even with no tenant membership', async () => {
    await open({ ...memberDetail, directPermissions: ['ARTICLE_CREATE'] }, false);
    selectPermissionsTab();

    const globalPermissions = TestBed.inject(GlobalPermissionsService);
    globalPermissions.fetch();
    httpMock
      .expectOne('/api/staff/permissions')
      .flush({ permissions: ['TENANT_PERMISSION_GRANT_CREATE'], isStaffAccount: true });
    fixture.detectChanges();

    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="permission-list-toggle-ARTICLE_CREATE"]',
    );
    expect(toggle.disabled).toBe(false);
  });

  it('hides "Save" with zero pending changes and shows it once a switch is toggled', async () => {
    await open(memberDetail, true);
    selectPermissionsTab();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-save-permissions-button"]'),
    ).toBeFalsy();

    fixture.nativeElement
      .querySelector('[data-testid="permission-list-toggle-ARTICLE_CREATE"]')
      .click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-save-permissions-button"]'),
    ).toBeTruthy();
  });

  it('clicking Save opens one confirm dialog and submits the full pending set on confirm', async () => {
    await open(memberDetail, true);
    selectPermissionsTab();

    fixture.nativeElement
      .querySelector('[data-testid="permission-list-toggle-ARTICLE_CREATE"]')
      .click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="member-save-permissions-button"]').click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/members/2/permissions/batch/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    const dialogEl = fixture.nativeElement.querySelector('app-confirm-dialog');
    const input: HTMLInputElement = dialogEl.querySelector('[data-testid="confirm-dialog-input"]');
    input.value = 'correct-horse';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    dialogEl.querySelector('[data-testid="confirm-dialog-confirm"]').click();

    const req = httpMock.expectOne('/api/tenants/1/members/2/permissions/batch');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      permissions: ['ARTICLE_CREATE'],
      word: 'correct-horse',
    });
    req.flush({});

    httpMock.expectOne('/api/tenants/1/members/2').flush(memberDetail);
  });

  it('does not offer inline access-group creation from this panel', async () => {
    await open(memberDetail, true);

    expect(
      fixture.nativeElement.querySelector('[data-testid="new-access-group-form"]'),
    ).toBeFalsy();
  });

  it('assigning and unassigning an access group updates the member shown state', async () => {
    await open(memberDetail, true, [{ id: 5, name: 'Support' }]);

    const assignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="assign-access-group-5"]',
    );
    assignButton.click();

    // Real backend returns ResponseEntity.ok().build() -- a genuinely empty body, which
    // Angular parses as `null`, not `{}`.
    httpMock.expectOne('/api/tenants/1/members/2/access-groups/5').flush(null);
    httpMock
      .expectOne('/api/tenants/1/members/2')
      .flush({ ...memberDetail, accessGroups: [{ id: 5, name: 'Support' }] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="unassign-access-group-5"]'),
    ).toBeTruthy();
  });

  it('renders a permission-denied state on a 403 from the detail fetch', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/members/2')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/tenants/1/access-groups').flush([]);
    httpMock.expectOne('/api/users/me/profile').flush(null);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('threads ownUserId into ProfileSectionComponent, hiding self-edit', async () => {
    await createFixture(true);
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/1/members/2').flush(memberDetail);
    httpMock.expectOne('/api/tenants/1/access-groups').flush([]);
    fixture.detectChanges();
    flushProfile();
    flushOwnProfile(1);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="member-edit-profile-button"]'),
    ).toBeFalsy();
  });
});
