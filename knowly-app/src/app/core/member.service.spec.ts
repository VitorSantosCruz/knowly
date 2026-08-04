import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MemberService } from './member.service';
import { MandatoryProfileFields } from './profile.service';

const PROFILE: MandatoryProfileFields = {
  fullName: 'Jane Doe',
  taxId: '111.111.111-11',
  countryCode: 'BR',
  address: {
    addressLine1: 'Main St, 123',
    addressLine2: null,
    city: 'Sao Paulo',
    stateRegion: 'SP',
    postalCode: '01000-000',
    countryCode: 'BR',
  },
  contacts: [{ type: 'EMAIL', value: 'new@example.com', label: null, isPrimary: true }],
};

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

  it('add() posts the new member with its mandatory profile', () => {
    service.add(1, 'new@example.com', 'MEMBER', PROFILE).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'new@example.com',
      role: 'MEMBER',
      profile: PROFILE,
    });
    req.flush({});
  });

  it('remove() deletes the membership with the confirmation word', () => {
    service.remove(1, 2, 'correct-horse').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ word: 'correct-horse' });
    req.flush({});
  });

  it('generateRemovalToken() fetches a fresh word', () => {
    let result: string | undefined;
    service.generateRemovalToken(1, 2).subscribe((word) => (result = word));

    const req = httpMock.expectOne('/api/tenants/1/members/2/deletion-confirmation-token');
    expect(req.request.method).toBe('POST');
    req.flush({ word: 'correct-horse' });

    expect(result).toBe('correct-horse');
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

  it('revokePermission() deletes the permission with the confirmation word', () => {
    service.revokePermission(1, 2, 'ARTICLE_VIEW', 'correct-horse').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/permissions/ARTICLE_VIEW');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ word: 'correct-horse' });
    req.flush({});
  });

  it('generatePermissionRevocationToken() fetches a fresh word', () => {
    let result: string | undefined;
    service
      .generatePermissionRevocationToken(1, 2, 'ARTICLE_VIEW')
      .subscribe((word) => (result = word));

    const req = httpMock.expectOne(
      '/api/tenants/1/members/2/permissions/ARTICLE_VIEW/deletion-confirmation-token',
    );
    expect(req.request.method).toBe('POST');
    req.flush({ word: 'correct-horse' });

    expect(result).toBe('correct-horse');
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

  it('unassignAccessGroup() deletes the assignment with the confirmation word', () => {
    service.unassignAccessGroup(1, 2, 3, 'correct-horse').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/access-groups/3');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ word: 'correct-horse' });
    req.flush({});
  });

  it('generateAccessGroupUnassignmentToken() fetches a fresh word', () => {
    let result: string | undefined;
    service.generateAccessGroupUnassignmentToken(1, 2, 3).subscribe((word) => (result = word));

    const req = httpMock.expectOne(
      '/api/tenants/1/members/2/access-groups/3/deletion-confirmation-token',
    );
    expect(req.request.method).toBe('POST');
    req.flush({ word: 'correct-horse' });

    expect(result).toBe('correct-horse');
  });

  it('demote() posts to the demote endpoint', () => {
    service.demote(1, 2).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/demote');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('promote() posts to the promote endpoint', () => {
    service.promote(1, 2).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/promote');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('generateHardDeleteToken() fetches a fresh word', () => {
    let result: string | undefined;
    service.generateHardDeleteToken(1, 2).subscribe((word) => (result = word));

    const req = httpMock.expectOne(
      '/api/tenants/1/members/2/hard-delete/deletion-confirmation-token',
    );
    expect(req.request.method).toBe('POST');
    req.flush({ word: 'correct-horse' });

    expect(result).toBe('correct-horse');
  });

  it('hardDelete() deletes the member with the confirmation word', () => {
    service.hardDelete(1, 2, 'correct-horse').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/hard-delete');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ word: 'correct-horse' });
    req.flush({});
  });

  it('generateBatchPermissionUpdateToken() fetches a fresh word', () => {
    let result: string | undefined;
    service.generateBatchPermissionUpdateToken(1, 2).subscribe((word) => (result = word));

    const req = httpMock.expectOne(
      '/api/tenants/1/members/2/permissions/batch/deletion-confirmation-token',
    );
    expect(req.request.method).toBe('POST');
    req.flush({ word: 'correct-horse' });

    expect(result).toBe('correct-horse');
  });

  it('batchUpdatePermissions() puts the full permission set with the confirmation word', () => {
    service.batchUpdatePermissions(1, 2, ['ARTICLE_CREATE'], 'correct-horse').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/members/2/permissions/batch');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      permissions: ['ARTICLE_CREATE'],
      word: 'correct-horse',
    });
    req.flush({});
  });
});
