import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { GlobalPermissionsService } from './global-permissions.service';

describe('GlobalPermissionsService', () => {
  let service: GlobalPermissionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(GlobalPermissionsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts with no known permissions', () => {
    expect(service.permissions()).toBeNull();
    expect(service.has('TENANT_CREATE')).toBe(false);
    expect(service.isStaffAccount()).toBe(false);
  });

  it('exposes the fetched permissions and has() reflects them', () => {
    service.fetch();

    const req = httpMock.expectOne('/api/staff/permissions');
    expect(req.request.method).toBe('GET');
    req.flush({ permissions: ['TENANT_CREATE'], isStaffAccount: true });

    expect(service.permissions()).toEqual(['TENANT_CREATE']);
    expect(service.has('TENANT_CREATE')).toBe(true);
    expect(service.has('TENANT_ACT_AS_ANY')).toBe(false);
    expect(service.isStaffAccount()).toBe(true);
  });

  it('exposes isStaffAccount: false for a plain member response', () => {
    service.fetch();

    httpMock.expectOne('/api/staff/permissions').flush({ permissions: [], isStaffAccount: false });

    expect(service.isStaffAccount()).toBe(false);
  });
});
