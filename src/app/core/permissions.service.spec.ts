import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { PermissionsService } from './permissions.service';

describe('PermissionsService', () => {
  let service: PermissionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PermissionsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('starts with no known permissions', () => {
    expect(service.permissions()).toBeNull();
    expect(service.has('ARTICLE_CREATE')).toBe(false);
  });

  it('exposes the fetched permissions and has() reflects them', () => {
    service.fetch();

    const req = httpMock.expectOne('/api/tenants/permissions');
    expect(req.request.method).toBe('GET');
    req.flush({ permissions: ['ARTICLE_VIEW', 'ARTICLE_CREATE'] });

    expect(service.permissions()).toEqual(['ARTICLE_VIEW', 'ARTICLE_CREATE']);
    expect(service.has('ARTICLE_CREATE')).toBe(true);
    expect(service.has('ARTICLE_DELETE')).toBe(false);
  });
});
