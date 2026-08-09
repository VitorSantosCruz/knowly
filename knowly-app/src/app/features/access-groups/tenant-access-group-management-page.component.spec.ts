import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { TenantAccessGroupManagementPageComponent } from './tenant-access-group-management-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { AccessGroup, MemberDetail } from '../../core/member.service';

function memberDetail(membershipId: number, accessGroups: AccessGroup[]): MemberDetail {
  return {
    membershipId,
    userId: membershipId,
    email: `member${membershipId}@example.com`,
    role: 'MEMBER',
    directPermissions: [],
    accessGroups,
    effectivePermissions: [],
    isLastAdminOfType: false,
  };
}

describe('TenantAccessGroupManagementPageComponent', () => {
  let fixture: ComponentFixture<TenantAccessGroupManagementPageComponent>;
  let httpMock: HttpTestingController;

  async function createFixture(
    opts: { memberAdmin?: boolean; globalPermissions?: string[] } = {},
  ): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [TenantAccessGroupManagementPageComponent],
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

    httpMock = TestBed.inject(HttpTestingController);
    const activeTenantService = TestBed.inject(ActiveTenantService);
    const globalPermissionsService = TestBed.inject(GlobalPermissionsService);

    (activeTenantService as unknown as { _activeTenantId: { set: (v: number) => void } })[
      '_activeTenantId'
    ].set(1);
    if (opts.memberAdmin) {
      (
        activeTenantService as unknown as {
          _activeTenantRole: { set: (v: string) => void };
        }
      )['_activeTenantRole'].set('MEMBER_ADMIN');
    }
    globalPermissionsService.fetch();
    const defaultPermissions = [
      'TENANT_ACCESS_GROUP_VIEW',
      'TENANT_ACCESS_GROUP_CREATE',
      'TENANT_ACCESS_GROUP_DELETE',
      'TENANT_PERMISSION_GRANT_CREATE',
      'TENANT_PERMISSION_GRANT_DELETE',
    ];
    httpMock.expectOne('/api/staff/permissions').flush({
      permissions: opts.globalPermissions ?? (opts.memberAdmin ? [] : defaultPermissions),
      isStaffAccount: true,
    });

    fixture = TestBed.createComponent(TenantAccessGroupManagementPageComponent);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders the access-group list via app-shared-list (REQ-1)', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();

    const list = fixture.nativeElement.querySelector('[data-testid="tenant-access-groups-list"]');
    expect(list).toBeTruthy();
  });

  it('shows NoAccessStateComponent on a 403 list load and issues no further requests (REQ-2/16)', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush({ message: 'denied' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
    httpMock.expectNone('/api/tenants/1/members');
  });

  it('shows ErrorStateComponent on a non-403 list load failure (REQ-16)', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush({ message: 'oops' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
  });

  it('creates a group and refreshes the list when the caller holds TENANT_ACCESS_GROUP_CREATE (REQ-4)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/access-groups').flush([]);
    fixture.detectChanges();

    fixture.componentInstance['newGroupName'].set('New Group');
    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="create-access-group-form"]',
    );
    form.dispatchEvent(new Event('submit'));

    const createReq = httpMock.expectOne('/api/tenants/1/access-groups');
    expect(createReq.request.method).toBe('POST');
    createReq.flush({});

    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 9, name: 'New Group', permissions: [] }]);
    fixture.detectChanges();

    expect(fixture.componentInstance['groups']()).toEqual([
      { id: 9, name: 'New Group', permissions: [] },
    ]);
  });

  it('does not offer the create-group control without TENANT_ACCESS_GROUP_CREATE (REQ-5)', async () => {
    await createFixture({ globalPermissions: ['TENANT_ACCESS_GROUP_VIEW'] });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/access-groups').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="create-access-group-form"]'),
    ).toBeFalsy();
  });

  it('does not render the roster/detail section while no group is selected (REQ-12)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-access-group-detail"]'),
    ).toBeFalsy();
  });

  it('selecting a group loads members list once plus getDetail once per member (REQ-3)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();

    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/1/members').flush([
      { membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' },
      { membershipId: 11, userId: 11, email: 'b@example.com', role: 'MEMBER' },
    ]);

    httpMock
      .expectOne('/api/tenants/1/members/10')
      .flush(memberDetail(10, [{ id: 1, name: 'Editors', permissions: [] }]));
    httpMock.expectOne('/api/tenants/1/members/11').flush(memberDetail(11, []));
    fixture.detectChanges();

    expect(fixture.componentInstance['currentRoster']().length).toBe(1);
    expect(fixture.componentInstance['assignableCandidates']().length).toBe(1);
  });

  it('switching groups within the same visit re-filters cached memberDetails without re-fetching (REQ-3 perf note)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/access-groups').flush([
      { id: 1, name: 'Editors', permissions: [] },
      { id: 2, name: 'Reviewers', permissions: [] },
    ]);
    fixture.detectChanges();

    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock
      .expectOne('/api/tenants/1/members/10')
      .flush(memberDetail(10, [{ id: 1, name: 'Editors', permissions: [] }]));
    fixture.detectChanges();

    fixture.componentInstance.selectGroup({ id: 2, name: 'Reviewers', permissions: [] });
    fixture.detectChanges();

    httpMock.expectNone('/api/tenants/1/members');
    httpMock.expectNone('/api/tenants/1/members/10');
  });

  it('grants a permission to the selected group when holding TENANT_PERMISSION_GRANT_CREATE (REQ-6)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    fixture.componentInstance['grantPermission']('ARTICLE_CREATE');

    const req = httpMock.expectOne('/api/tenants/1/access-groups/1/permissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ permission: 'ARTICLE_CREATE' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('selecting a role renders app-permission-list seeded from group.permissions (REQ-6)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: ['ARTICLE_VIEW'] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({
      id: 1,
      name: 'Editors',
      permissions: ['ARTICLE_VIEW'],
    });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-permission-list')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="grant-permission-form"]'),
    ).toBeFalsy();

    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="permission-list-toggle-ARTICLE_VIEW"]',
    );
    expect(toggle.getAttribute('aria-checked')).toBe('true');
  });

  it('toggling a row on calls grantAccessGroupPermission (REQ-6/9)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="permission-list-toggle-ARTICLE_VIEW"]')
      .click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement
        .querySelector('[data-testid="permission-list-toggle-ARTICLE_VIEW"]')
        .getAttribute('aria-checked'),
    ).toBe('true');

    const req = httpMock.expectOne('/api/tenants/1/access-groups/1/permissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ permission: 'ARTICLE_VIEW' });
    req.flush(null, { status: 200, statusText: 'OK' });
  });

  it('toggling a row off calls revokeAccessGroupPermission (REQ-6/9)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: ['ARTICLE_VIEW'] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({
      id: 1,
      name: 'Editors',
      permissions: ['ARTICLE_VIEW'],
    });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="permission-list-toggle-ARTICLE_VIEW"]')
      .click();
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/tenants/1/access-groups/1/permissions/ARTICLE_VIEW');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 200, statusText: 'OK' });
  });

  it('a failed grant/revoke reverts the row and shows an inline error, without blanking the roster (REQ-10)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('[data-testid="permission-list-toggle-ARTICLE_VIEW"]')
      .click();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/access-groups/1/permissions')
      .flush({ message: 'oops' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement
        .querySelector('[data-testid="permission-list-toggle-ARTICLE_VIEW"]')
        .getAttribute('aria-checked'),
    ).toBe('false');
    expect(
      fixture.nativeElement.querySelector('[data-testid="permission-action-error"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-access-groups-list"]'),
    ).toBeTruthy();
  });

  it('a second click on the same row while its call is in flight is a no-op (REQ-10 double-click guard)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="permission-list-toggle-ARTICLE_VIEW"]',
    );
    toggle.click();
    fixture.detectChanges();
    toggle.click();
    fixture.detectChanges();

    // Only one request fired -- the second click while in flight was ignored.
    httpMock.expectOne('/api/tenants/1/access-groups/1/permissions').flush(null, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('single-assigns a candidate to one group and re-fetches the roster (REQ-7/REQ-10)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    fixture.componentInstance['onAssignmentSubmitted'](10, [1]);

    const assignReq = httpMock.expectOne('/api/tenants/1/members/10/access-groups/1');
    expect(assignReq.request.method).toBe('POST');
    assignReq.flush(null, { status: 204, statusText: 'No Content' });

    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock
      .expectOne('/api/tenants/1/members/10')
      .flush(memberDetail(10, [{ id: 1, name: 'Editors', permissions: [] }]));
  });

  it('bulk-assigns a candidate to several groups via the batch endpoint (REQ-9/10)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/access-groups').flush([
      { id: 1, name: 'Editors', permissions: [] },
      { id: 2, name: 'Reviewers', permissions: [] },
    ]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    fixture.componentInstance['onAssignmentSubmitted'](10, [1, 2]);

    const batchReq = httpMock.expectOne('/api/tenants/1/members/10/access-groups:batch');
    expect(batchReq.request.method).toBe('POST');
    expect(batchReq.request.body).toEqual({ accessGroupIds: [1, 2] });
    batchReq.flush(null, { status: 204, statusText: 'No Content' });

    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(
      memberDetail(10, [
        { id: 1, name: 'Editors', permissions: [] },
        { id: 2, name: 'Reviewers', permissions: [] },
      ]),
    );
  });

  it('bulk-assign 400 shows the generic error branch, not permission-denied (REQ-10)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/access-groups').flush([
      { id: 1, name: 'Editors', permissions: [] },
      { id: 2, name: 'Reviewers', permissions: [] },
    ]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    fixture.componentInstance['onAssignmentSubmitted'](10, [1, 2]);

    httpMock
      .expectOne('/api/tenants/1/members/10/access-groups:batch')
      .flush({ message: 'bad' }, { status: 400, statusText: 'Bad Request' });

    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    expect(fixture.componentInstance['error']()).toBe('network');
  });

  it('bulk-assign 403 surfaces permission-denied and reflects no assignment (REQ-11)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/access-groups').flush([
      { id: 1, name: 'Editors', permissions: [] },
      { id: 2, name: 'Reviewers', permissions: [] },
    ]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    fixture.componentInstance['onAssignmentSubmitted'](10, [1, 2]);

    httpMock
      .expectOne('/api/tenants/1/members/10/access-groups:batch')
      .flush({ message: 'denied' }, { status: 403, statusText: 'Forbidden' });

    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock.expectOne('/api/tenants/1/members/10').flush(memberDetail(10, []));
    fixture.detectChanges();

    expect(fixture.componentInstance['error']()).toBe('permission-denied');
  });

  it('unassigns a roster member via the confirm-dialog round trip and re-fetches the roster (REQ-8)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/members')
      .flush([{ membershipId: 10, userId: 10, email: 'a@example.com', role: 'MEMBER' }]);
    httpMock
      .expectOne('/api/tenants/1/members/10')
      .flush(memberDetail(10, [{ id: 1, name: 'Editors', permissions: [] }]));
    fixture.detectChanges();

    fixture.componentInstance['onUnassign'](10);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/members/10/access-groups/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    fixture.componentInstance['confirmUnassign']('correct-horse');

    const unassignReq = httpMock.expectOne('/api/tenants/1/members/10/access-groups/1');
    expect(unassignReq.request.method).toBe('DELETE');
    unassignReq.flush(null, { status: 204, statusText: 'No Content' });

    httpMock.expectOne('/api/tenants/1/members').flush([]);
  });

  it('deletes a group via the confirm-dialog round trip, removing it from the list and clearing the selection (REQ-13/14)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    fixture.componentInstance['onDeleteGroup']({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();

    httpMock
      .expectOne('/api/tenants/1/access-groups/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    fixture.componentInstance['confirmDelete']('correct-horse');

    const deleteReq = httpMock.expectOne('/api/tenants/1/access-groups/1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(fixture.componentInstance['groups']()).toEqual([]);
    expect(fixture.componentInstance['selectedGroup']()).toBeNull();
  });

  it('delete request 403 leaves the group in the list (REQ-15)', async () => {
    await createFixture();
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups')
      .flush([{ id: 1, name: 'Editors', permissions: [] }]);
    fixture.detectChanges();
    fixture.componentInstance.selectGroup({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/1/members').flush([]);
    fixture.detectChanges();

    fixture.componentInstance['onDeleteGroup']({ id: 1, name: 'Editors', permissions: [] });
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/1/access-groups/1/deletion-confirmation-token')
      .flush({ word: 'correct-horse' });
    fixture.detectChanges();

    fixture.componentInstance['confirmDelete']('correct-horse');
    httpMock
      .expectOne('/api/tenants/1/access-groups/1')
      .flush({ message: 'denied' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.componentInstance['groups']()).toEqual([
      { id: 1, name: 'Editors', permissions: [] },
    ]);
    expect(fixture.componentInstance['error']()).toBe('permission-denied');
  });
});
