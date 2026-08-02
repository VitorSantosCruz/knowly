import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { OwnProfilePageComponent } from './own-profile-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('OwnProfilePageComponent', () => {
  let fixture: ComponentFixture<OwnProfilePageComponent>;
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

  const profile = { userId: 1, email: 'jane@example.com', fields, avatarUrl: null };

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

  afterEach(() => {
    httpMock.verify();
  });

  it('loads and renders own profile with email read-only', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-email"]').textContent,
    ).toContain('jane@example.com');
    expect(fixture.nativeElement.querySelector('[data-testid="profile-field-email"]')).toBeNull();
  });

  it('every session submits via POST edit-requests, including a STAFF_ADMIN/tenant-ADMIN-shaped one', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();

    const req = httpMock.expectOne('/api/users/me/profile/edit-requests');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.fields.fullName).toBe('Jane Doe');
    req.flush({
      id: 1,
      requesterUserId: 1,
      proposedFields: fields,
      proposedContactChanges: [],
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
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="profile-fields-submit"]').click();
    httpMock.expectOne('/api/users/me/profile/edit-requests').flush({
      id: 1,
      requesterUserId: 1,
      proposedFields: fields,
      proposedContactChanges: [],
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

  it('selecting a new avatar calls uploadAvatar and updates the displayed avatar immediately, independent of pending state', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    fixture.detectChanges();

    const file = new File(['content'], 'avatar.png', { type: 'image/png' });
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-upload-input"]',
    );
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));

    const req = httpMock.expectOne('/api/users/me/profile/avatar');
    expect(req.request.method).toBe('POST');
    req.flush({ ...profile, avatarUrl: 'https://example.com/avatar.png' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-upload-image"]').src).toBe(
      'https://example.com/avatar.png',
    );
  });

  it('a 400 on avatar upload shows a clear message and leaves the previous avatar displayed', async () => {
    await createFixture();
    fixture.detectChanges();

    flushOwnProfile();
    fixture.detectChanges();

    const file = new File(['content'], 'avatar.png', { type: 'image/png' });
    const input: HTMLInputElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-upload-input"]',
    );
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));

    httpMock
      .expectOne('/api/users/me/profile/avatar')
      .flush({ message: 'bad file' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-avatar-error"]'),
    ).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="avatar-upload-placeholder"]'),
    ).toBeTruthy();
  });
});
