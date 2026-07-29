import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { MemberDetailPanelComponent } from './member-detail-panel.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('MemberDetailPanelComponent', () => {
  let fixture: ComponentFixture<MemberDetailPanelComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
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
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function setInputsAndFlushDetail(viewerIsMemberAdminOfThisTenant = false) {
    fixture.componentRef.setInput('tenantId', 7);
    fixture.componentRef.setInput('membershipId', 1);
    fixture.componentRef.setInput(
      'viewerIsMemberAdminOfThisTenant',
      viewerIsMemberAdminOfThisTenant,
    );
    fixture.detectChanges();

    httpMock.expectOne('/api/tenants/7/members/1').flush({
      membershipId: 1,
      userId: 42,
      email: 'a@example.com',
      role: 'MEMBER',
      directPermissions: ['ARTICLE_VIEW'],
      accessGroups: [{ id: 3, name: 'Editors' }],
      effectivePermissions: ['ARTICLE_VIEW', 'ARTICLE_EDIT'],
    });
    httpMock.expectOne('/api/tenants/7/access-groups').flush([{ id: 3, name: 'Editors' }]);
    fixture.detectChanges();
  }

  const profileFields = {
    fullName: 'Member A',
    cpf: '111.111.111-11',
    rg: '11.111.111-1',
    rgOrgaoEmissor: 'SSP',
    birthDate: '1990-01-01',
    address: null,
    contacts: [],
  };

  function flushProfile(): void {
    httpMock.expectOne('/api/users/42/profile').flush({
      userId: 42,
      email: 'a@example.com',
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

  it('shows direct permissions, access groups, and effective permissions as distinct sections', () => {
    setInputsAndFlushDetail();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    const html = fixture.nativeElement.innerHTML;
    expect(fixture.nativeElement.querySelector('[data-testid="direct-permissions"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="access-groups"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="effective-permissions"]'),
    ).toBeTruthy();
    expect(html).toContain('Editors');
  });

  it('toggling a permission grants it and re-fetches the detail', () => {
    setInputsAndFlushDetail();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    const toggle: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="permission-toggle-ARTICLE_CREATE"]',
    );
    toggle.click();

    httpMock.expectOne('/api/tenants/7/members/1/permissions').flush({});
    httpMock.expectOne('/api/tenants/7/members/1').flush({
      membershipId: 1,
      userId: 42,
      email: 'a@example.com',
      role: 'MEMBER',
      directPermissions: ['ARTICLE_VIEW', 'ARTICLE_CREATE'],
      accessGroups: [],
      effectivePermissions: ['ARTICLE_VIEW', 'ARTICLE_CREATE'],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('ARTICLE_CREATE');
  });

  it('creating an access group makes it available to assign', () => {
    setInputsAndFlushDetail();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    const nameInput: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="new-access-group-name"]',
    );
    nameInput.value = 'Reviewers';
    nameInput.dispatchEvent(new Event('input'));

    const form: HTMLFormElement = fixture.nativeElement.querySelector(
      '[data-testid="new-access-group-form"]',
    );
    form.dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/tenants/7/access-groups').flush({});
    httpMock.expectOne('/api/tenants/7/access-groups').flush([
      { id: 3, name: 'Editors' },
      { id: 4, name: 'Reviewers' },
    ]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="assign-access-group-4"]'),
    ).toBeTruthy();
  });

  it('assigning an access group updates the member access groups', () => {
    setInputsAndFlushDetail();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    fixture.componentInstance['availableAccessGroups'].set([
      { id: 3, name: 'Editors' },
      { id: 4, name: 'Reviewers' },
    ]);
    fixture.detectChanges();

    const assignButton: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="assign-access-group-4"]',
    );
    assignButton.click();

    httpMock.expectOne('/api/tenants/7/members/1/access-groups/4').flush({});
    httpMock.expectOne('/api/tenants/7/members/1').flush({
      membershipId: 1,
      userId: 42,
      email: 'a@example.com',
      role: 'MEMBER',
      directPermissions: [],
      accessGroups: [
        { id: 3, name: 'Editors' },
        { id: 4, name: 'Reviewers' },
      ],
      effectivePermissions: [],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Reviewers');
  });

  it('renders the profile section alongside its other, untouched sections', () => {
    setInputsAndFlushDetail();
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-section"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="effective-permissions"]'),
    ).toBeTruthy();
  });

  it('disables profile editing for a non-admin viewer without PROFILE_EDIT', () => {
    setInputsAndFlushDetail(false);
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]'),
    ).toBeFalsy();
  });

  it('enables profile editing for a member admin of this tenant', () => {
    setInputsAndFlushDetail(true);
    flushProfile();
    flushOwnProfile();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]'),
    ).toBeTruthy();
  });

  it('threads ownUserId into ProfileSectionComponent, sourced from one getOwnProfile() call per panel-open', () => {
    setInputsAndFlushDetail(true);
    flushProfile();
    flushOwnProfile(42);
    fixture.detectChanges();

    // ownUserId === userId (42) hides the edit toggle even though the viewer is a member admin.
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]'),
    ).toBeNull();
  });
});
