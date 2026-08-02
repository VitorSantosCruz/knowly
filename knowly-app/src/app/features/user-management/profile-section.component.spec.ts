import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { ProfileSectionComponent } from './profile-section.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('ProfileSectionComponent', () => {
  let fixture: ComponentFixture<ProfileSectionComponent>;
  let httpMock: HttpTestingController;

  const fields = {
    fullName: 'Jane Doe',
    taxId: '111.111.111-11',
    countryCode: 'BR',
    address: {
      addressLine1: 'Main St, 123',
      addressLine2: 'Centro',
      city: 'Sao Paulo',
      stateRegion: 'SP',
      postalCode: '01000-000',
      countryCode: 'BR',
    },
    contacts: [{ id: 1, type: 'PHONE', value: '+5511987654321', label: null, isPrimary: true }],
  };

  const profile = { userId: 42, email: 'jane@example.com', fields, avatarUrl: null };

  async function createFixture(canEdit = false, ownUserId: number | null = null): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ProfileSectionComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileSectionComponent);
    fixture.componentRef.setInput('userId', 42);
    fixture.componentRef.setInput('canEdit', canEdit);
    fixture.componentRef.setInput('ownUserId', ownUserId);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it("renders GET /api/users/{id}/profile's fields, including the read-only avatar", async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/users/42/profile')
      .flush({ ...profile, avatarUrl: 'https://example.com/a.png' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section"]').textContent,
    ).toContain('jane@example.com');
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-avatar"]'),
    ).toBeTruthy();
  });

  it('renders the read-only avatar regardless of canEdit', async () => {
    await createFixture(true, 999);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/users/42/profile')
      .flush({ ...profile, avatarUrl: 'https://example.com/a.png' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-avatar"]'),
    ).toBeTruthy();
  });

  it('a 403 renders app-no-access-state scoped to this section only', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/users/42/profile')
      .flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="no-access-state"]')).toBeTruthy();
  });

  it('[canEdit]=true and a different ownUserId reveals an edit toggle; submitting calls PUT with {fields, contactChanges} and refreshes', async () => {
    await createFixture(true, 999);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush(profile);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/users/42/profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      fields: { ...fields, contacts: [{ ...fields.contacts[0], rowKey: 'id-1' }] },
      contactChanges: [],
    });
    req.flush({ ...profile, fields: { ...fields, fullName: 'Jane Updated' } });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section"]').textContent,
    ).toContain('Jane Updated');
  });

  it('the contacts editor is shown by default in the direct-edit form (backend now applies contact changes)', async () => {
    await createFixture(true, 999);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush(profile);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]').click();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-contacts-fieldset"]'),
    ).toBeTruthy();
  });

  it('a 409 on the edit call shows the conflict message', async () => {
    await createFixture(true, 999);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush(profile);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
    httpMock
      .expectOne('/api/users/42/profile')
      .flush({ conflictingFields: ['taxId'] }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-conflict"]'),
    ).toBeTruthy();
  });

  it('[canEdit]=false never renders the edit toggle', async () => {
    await createFixture(false, 999);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush(profile);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]'),
    ).toBeNull();
  });

  it('[ownUserId] equal to [userId] hides the edit toggle even when canEdit=true', async () => {
    await createFixture(true, 42);
    fixture.detectChanges();

    httpMock.expectOne('/api/users/42/profile').flush(profile);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-section-edit-toggle"]'),
    ).toBeNull();
  });
});
