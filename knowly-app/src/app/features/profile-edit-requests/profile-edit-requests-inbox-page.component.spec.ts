import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { ProfileEditRequestsInboxPageComponent } from './profile-edit-requests-inbox-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('ProfileEditRequestsInboxPageComponent', () => {
  let fixture: ComponentFixture<ProfileEditRequestsInboxPageComponent>;
  let httpMock: HttpTestingController;

  const request = {
    id: 7,
    requesterUserId: 3,
    requesterName: 'Jane Doe',
    requesterEmail: 'jane@example.com',
    proposedFields: {
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
      contacts: [],
    },
    proposedContactChanges: [
      {
        action: 'ADD',
        contactId: null,
        type: 'EMAIL',
        value: 'jane@example.com',
        label: null,
        isPrimary: true,
      },
    ],
    status: 'PENDING',
    createdAt: '2026-07-28T10:00:00Z',
  };

  async function createFixture(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [ProfileEditRequestsInboxPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileEditRequestsInboxPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('renders every pending request (requester id, proposed fields, structured address, contact changes, submission date)', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([request]);
    fixture.detectChanges();

    const text = fixture.nativeElement.querySelector(
      '[data-testid="profile-edit-request-7"]',
    ).textContent;
    expect(text).toContain('3');
    expect(text).toContain('Jane Doe');
    expect(text).toContain('2026-07-28T10:00:00Z');

    const addressText = fixture.nativeElement.querySelector(
      '[data-testid="profile-edit-request-address-7"]',
    ).textContent;
    expect(addressText).toContain('Main St');
    expect(addressText).toContain('Sao Paulo');

    const contactChangesText = fixture.nativeElement.querySelector(
      '[data-testid="profile-edit-request-contact-changes-7"]',
    ).textContent;
    expect(contactChangesText).toContain('ADD');
    expect(contactChangesText).toContain('jane@example.com');
  });

  it('renders the requester name when present', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([request]);
    fixture.detectChanges();

    const text = fixture.nativeElement.querySelector(
      '[data-testid="profile-edit-request-7"]',
    ).textContent;
    expect(text).toContain('Jane Doe');
  });

  it('falls back to the requester email when requesterName is null', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([{ ...request, requesterName: null }]);
    fixture.detectChanges();

    const text = fixture.nativeElement.querySelector(
      '[data-testid="profile-edit-request-7"]',
    ).textContent;
    expect(text).toContain('jane@example.com');
  });

  it('falls back to "User #{id}" when both requesterName and requesterEmail are null', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock
      .expectOne('/api/profile-edit-requests')
      .flush([{ ...request, requesterName: null, requesterEmail: null }]);
    fixture.detectChanges();

    const text = fixture.nativeElement.querySelector(
      '[data-testid="profile-edit-request-7"]',
    ).textContent;
    expect(text).toContain('3');
  });

  it('renders the distinct empty state when there are zero requests', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([]);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-edit-requests-empty"]'),
    ).toBeTruthy();
  });

  it('approving a row calls POST .../approve and removes it from the list', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([request]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="approve-request-7"]').click();

    const req = httpMock.expectOne('/api/profile-edit-requests/7/approve');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-edit-request-7"]'),
    ).toBeNull();
  });

  it('rejecting a row calls POST .../reject and removes it from the list', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([request]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="reject-request-7"]').click();

    const req = httpMock.expectOne('/api/profile-edit-requests/7/reject');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-edit-request-7"]'),
    ).toBeNull();
  });

  it('a 409 on approve keeps the row visible and shows the conflict message', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([request]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="approve-request-7"]').click();
    httpMock
      .expectOne('/api/profile-edit-requests/7/approve')
      .flush({ conflictingFields: ['taxId'] }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-edit-request-7"]'),
    ).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="conflict-request-7"]')).toBeTruthy();
  });

  it('a 403 or non-uniqueness 409 refreshes the list and shows the error state', async () => {
    await createFixture();
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([request]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="approve-request-7"]').click();
    httpMock
      .expectOne('/api/profile-edit-requests/7/approve')
      .flush({ message: 'forbidden' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    httpMock.expectOne('/api/profile-edit-requests').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="profile-edit-request-7"]'),
    ).toBeNull();
  });
});
