import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let httpMock: HttpTestingController;

  const fields = {
    fullName: 'Jane Doe',
    address: '123 Main St',
    rg: '11.111.111-1',
    cpf: '111.111.111-11',
    phone: '+15550000',
  };

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
    req.flush({ userId: 1, email: 'jane@example.com', ...fields });
  });

  it('getProfile(userId) calls GET /api/users/{id}/profile', () => {
    service.getProfile(42).subscribe();

    const req = httpMock.expectOne('/api/users/42/profile');
    expect(req.request.method).toBe('GET');
    req.flush({ userId: 42, email: 'jane@example.com', ...fields });
  });

  it('directEdit(userId, fields) calls PUT /api/users/{id}/profile', () => {
    service.directEdit(42, fields).subscribe();

    const req = httpMock.expectOne('/api/users/42/profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(fields);
    req.flush({ userId: 42, email: 'jane@example.com', ...fields });
  });

  it('submitEditRequest(fields) calls POST /api/users/me/profile/edit-requests', () => {
    service.submitEditRequest(fields).subscribe();

    const req = httpMock.expectOne('/api/users/me/profile/edit-requests');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(fields);
    req.flush({
      id: 1,
      requesterUserId: 1,
      proposedFields: fields,
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
});
