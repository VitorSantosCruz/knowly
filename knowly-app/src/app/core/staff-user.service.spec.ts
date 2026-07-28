import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { StaffUserService } from './staff-user.service';

describe('StaffUserService', () => {
  let service: StaffUserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StaffUserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() fetches every staff user', () => {
    service.list().subscribe();

    const req = httpMock.expectOne('/api/staff/users');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('list(email) filters by email', () => {
    service.list('bob@example.com').subscribe();

    const req = httpMock.expectOne('/api/staff/users?email=bob@example.com');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('create() posts the new staff user email', () => {
    service.create('new@example.com').subscribe();

    const req = httpMock.expectOne('/api/staff/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'new@example.com' });
    req.flush({});
  });

  it('getDetail() fetches a staff user detail', () => {
    service.getDetail(1).subscribe();

    const req = httpMock.expectOne('/api/staff/users/1/permissions');
    expect(req.request.method).toBe('GET');
    req.flush({
      userId: 1,
      email: 'x@example.com',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
  });

  it('grantPermission() posts the permission', () => {
    service.grantPermission(1, 'STAFF_USER_CREATE').subscribe();

    const req = httpMock.expectOne('/api/staff/users/1/permissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ permission: 'STAFF_USER_CREATE' });
    req.flush({});
  });

  it('revokePermission() deletes the permission', () => {
    service.revokePermission(1, 'STAFF_USER_CREATE').subscribe();

    const req = httpMock.expectOne('/api/staff/users/1/permissions/STAFF_USER_CREATE');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('listAccessGroups() fetches the global access groups', () => {
    service.listAccessGroups().subscribe();

    const req = httpMock.expectOne('/api/staff/access-groups');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('createAccessGroup() posts the new group', () => {
    service.createAccessGroup('Support').subscribe();

    const req = httpMock.expectOne('/api/staff/access-groups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Support' });
    req.flush({});
  });

  it('grantAccessGroupPermission() posts the permission to the group', () => {
    service.grantAccessGroupPermission(3, 'STAFF_USER_CREATE').subscribe();

    const req = httpMock.expectOne('/api/staff/access-groups/3/permissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ permission: 'STAFF_USER_CREATE' });
    req.flush({});
  });

  it('assignAccessGroup() posts the assignment', () => {
    service.assignAccessGroup(1, 3).subscribe();

    const req = httpMock.expectOne('/api/staff/users/1/access-groups/3');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('unassignAccessGroup() deletes the assignment', () => {
    service.unassignAccessGroup(1, 3).subscribe();

    const req = httpMock.expectOne('/api/staff/users/1/access-groups/3');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
