import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { OwnProfilePageComponent } from './own-profile-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('OwnProfilePageComponent', () => {
  let fixture: ComponentFixture<OwnProfilePageComponent>;
  let httpMock: HttpTestingController;

  const profile = {
    userId: 1,
    email: 'jane@example.com',
    fullName: 'Jane Doe',
    address: '123 Main St',
    rg: '11.111.111-1',
    cpf: '111.111.111-11',
    phone: '+15550000',
  };

  async function createFixture(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OwnProfilePageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OwnProfilePageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushOwnProfile(): void {
    httpMock.expectOne('/api/users/me/profile').flush(profile);
  }

  function flushGlobalPermissions(permissions: string[] = []): void {
    httpMock.expectOne('/api/staff/permissions').flush({ permissions });
  }

  function flushMemberships(memberships: unknown[] = []): void {
    httpMock.expectOne('/api/tenants/memberships').flush(memberships);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders own profile with email read-only', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions();
    flushMemberships();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-email"]').textContent).toContain(
      'jane@example.com',
    );
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-email"]')).toBeNull();
  });

  it('a STAFF_ADMIN-shaped session submits via PUT and applies the result immediately', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions([
      'TENANT_CREATE',
      'TENANT_ACT_AS_ANY',
      'TENANT_MEMBER_MANAGE_ANY',
      'TENANT_ACCESS_GROUP_MANAGE_ANY',
      'TENANT_PERMISSION_GRANT_MANAGE_ANY',
      'STAFF_PERMISSION_MANAGE',
      'STAFF_USER_CREATE',
      'STAFF_USER_VIEW',
      'DASHBOARD_VIEW_GLOBAL',
      'AUDIT_TRAIL_VIEW',
      'PROFILE_EDIT',
    ]);
    flushMemberships();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/users/1/profile');
    expect(req.request.method).toBe('PUT');
    req.flush({ ...profile, fullName: 'Jane Updated' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-fullName"]').value).toBe(
      'Jane Updated',
    );
  });

  it('a tenant ADMIN membership session submits via the same PUT branch', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions();
    flushMemberships([{ tenantId: 1, tenantName: 'Acme', role: 'ADMIN', active: true }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/users/1/profile');
    expect(req.request.method).toBe('PUT');
    req.flush(profile);
  });

  it('a plain session submits via POST edit-requests instead and enters pending state', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions();
    flushMemberships([{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/users/me/profile/edit-requests');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 1,
      requesterUserId: 1,
      proposedFields: profile,
      status: 'PENDING',
      createdAt: '2026-07-28T00:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-pending"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').disabled,
    ).toBe(true);
  });

  it('blocks a second submission attempt while already pending', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions();
    flushMemberships([{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
    httpMock
      .expectOne('/api/users/me/profile/edit-requests')
      .flush({
        id: 1,
        requesterUserId: 1,
        proposedFields: profile,
        status: 'PENDING',
        createdAt: '2026-07-28T00:00:00Z',
      });
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
    httpMock.expectNone('/api/users/me/profile/edit-requests');
  });

  it('shows the already-pending message on a 409 from the edit-request call', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions();
    flushMemberships([{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
    httpMock
      .expectOne('/api/users/me/profile/edit-requests')
      .flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-pending"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').disabled,
    ).toBe(true);
  });

  it('shows a conflict message and preserves the form values on a direct-edit 409', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    flushGlobalPermissions([
      'TENANT_CREATE',
      'TENANT_ACT_AS_ANY',
      'TENANT_MEMBER_MANAGE_ANY',
      'TENANT_ACCESS_GROUP_MANAGE_ANY',
      'TENANT_PERMISSION_GRANT_MANAGE_ANY',
      'STAFF_PERMISSION_MANAGE',
      'STAFF_USER_CREATE',
      'STAFF_USER_VIEW',
      'DASHBOARD_VIEW_GLOBAL',
      'AUDIT_TRAIL_VIEW',
      'PROFILE_EDIT',
    ]);
    flushMemberships();
    fixture.detectChanges();

    const fullNameInput = fixture.nativeElement.querySelector(
      '[data-testid="profile-field-fullName"]',
    );
    fullNameInput.value = 'Jane Changed';
    fullNameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
    httpMock
      .expectOne('/api/users/1/profile')
      .flush({ conflictingFields: ['cpf'] }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="profile-conflict"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-field-fullName"]').value,
    ).toBe('Jane Changed');
  });
});
