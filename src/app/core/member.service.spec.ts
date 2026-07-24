import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MemberService } from './member.service';

describe('MemberService', () => {
  let service: MemberService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MemberService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() fetches the members of a tenant', () => {
    service.list(1).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('add() posts the new member', () => {
    service.add(1, 'new@example.com', 'MEMBER').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'new@example.com', role: 'MEMBER' });
    req.flush({});
  });

  it('remove() deletes the membership', () => {
    service.remove(1, 2).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('getDetail() fetches a member detail', () => {
    service.getDetail(1, 2).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2');
    expect(req.request.method).toBe('GET');
    req.flush({
      membershipId: 2,
      email: 'x@example.com',
      role: 'MEMBER',
      directPermissions: [],
      accessGroups: [],
      effectivePermissions: [],
    });
  });

  it('grantPermission() posts the permission', () => {
    service.grantPermission(1, 2, 'ARTICLE_VIEW').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/permissions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ permission: 'ARTICLE_VIEW' });
    req.flush({});
  });

  it('revokePermission() deletes the permission', () => {
    service.revokePermission(1, 2, 'ARTICLE_VIEW').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/permissions/ARTICLE_VIEW');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('listAccessGroups() fetches the tenant access groups', () => {
    service.listAccessGroups(1).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/access-groups');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('createAccessGroup() posts the new group', () => {
    service.createAccessGroup(1, 'Editors').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/access-groups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Editors' });
    req.flush({});
  });

  it('assignAccessGroup() posts the assignment', () => {
    service.assignAccessGroup(1, 2, 3).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/access-groups/3');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('unassignAccessGroup() deletes the assignment', () => {
    service.unassignAccessGroup(1, 2, 3).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/access-groups/3');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
