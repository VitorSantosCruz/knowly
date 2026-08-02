import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import {
  ProfileService,
  ProfileFields,
  ContactChange,
  MandatoryProfileFields,
} from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  const fields: ProfileFields = {
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
    contacts: [{ id: 1, type: 'PHONE', value: '+15550000', label: null, isPrimary: true }],
  };

  const contactChanges: ContactChange[] = [
    {
      action: 'ADD',
      contactId: null,
      type: 'EMAIL',
      value: 'jane@example.com',
      label: null,
      isPrimary: true,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('getOwnProfile() calls GET /api/users/me/profile', () => {
    service.getOwnProfile().subscribe();

    const req = httpMock.expectOne('/api/users/me/profile');
    expect(req.request.method).toBe('GET');
    req.flush({ userId: 1, email: 'jane@example.com', fields, avatarUrl: null });
  });

  it('getProfile(userId) calls GET /api/users/{id}/profile', () => {
    service.getProfile(42).subscribe();

    const req = httpMock.expectOne('/api/users/42/profile');
    expect(req.request.method).toBe('GET');
    req.flush({ userId: 42, email: 'jane@example.com', fields, avatarUrl: null });
  });

  it('directEdit(userId, fields, contactChanges) calls PUT /api/users/{id}/profile with a {fields, contactChanges} body', () => {
    service.directEdit(42, fields, contactChanges).subscribe();

    const req = httpMock.expectOne('/api/users/42/profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ fields, contactChanges });
    req.flush({ userId: 42, email: 'jane@example.com', fields, avatarUrl: null });
  });

  it('uploadAvatar(file) posts FormData to POST /api/users/me/profile/avatar', () => {
    const file = new File(['content'], 'avatar.png', { type: 'image/png' });
    service.uploadAvatar(file).subscribe();

    const req = httpMock.expectOne('/api/users/me/profile/avatar');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({
      userId: 1,
      email: 'jane@example.com',
      fields,
      avatarUrl: 'https://example.com/avatar.png',
    });
  });

  it('submitEditRequest(fields, contactChanges) calls POST /api/users/me/profile/edit-requests with both', () => {
    service.submitEditRequest(fields, contactChanges).subscribe();

    const req = httpMock.expectOne('/api/users/me/profile/edit-requests');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ fields, contactChanges });
    req.flush({
      id: 1,
      requesterUserId: 1,
      requesterName: 'Jane Doe',
      requesterEmail: 'jane@example.com',
      proposedFields: fields,
      proposedContactChanges: contactChanges,
      status: 'PENDING',
      createdAt: '2026-07-28T00:00:00Z',
    });
  });

  it('listEditRequests() calls GET /api/profile-edit-requests', () => {
    service.listEditRequests().subscribe();

    const req = httpMock.expectOne('/api/profile-edit-requests');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('approveEditRequest(id) calls POST /api/profile-edit-requests/{id}/approve', () => {
    service.approveEditRequest(7).subscribe();

    const req = httpMock.expectOne('/api/profile-edit-requests/7/approve');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('rejectEditRequest(id) calls POST /api/profile-edit-requests/{id}/reject', () => {
    service.rejectEditRequest(7).subscribe();

    const req = httpMock.expectOne('/api/profile-edit-requests/7/reject');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('completeOwnProfile(dto) calls POST /api/users/me/profile/complete with the given body', () => {
    const dto: MandatoryProfileFields = {
      ...fields,
      contacts: [{ type: 'PHONE', value: '+15550000', label: null, isPrimary: true }],
    };
    service.completeOwnProfile(dto).subscribe();

    const req = httpMock.expectOne('/api/users/me/profile/complete');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush({ userId: 1, email: 'jane@example.com', fields, avatarUrl: null });
  });
});
