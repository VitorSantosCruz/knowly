import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { createMetricFetcher } from './metric-fetcher';

describe('createMetricFetcher', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('forwards params to HttpClient#get when load(params) is called', () => {
    const http = TestBed.inject(HttpClient);
    const httpMock = TestBed.inject(HttpTestingController);
    const fetcher = createMetricFetcher<{ totalCount: number }>(http, '/api/tenants/metrics/foo');

    fetcher.load({ period: '30d' });

    const req = httpMock.expectOne(
      (request) => request.url === '/api/tenants/metrics/foo' && request.params.get('period') === '30d',
    );
    req.flush({ totalCount: 1 });

    httpMock.verify();
  });

  it('still works with no params', () => {
    const http = TestBed.inject(HttpClient);
    const httpMock = TestBed.inject(HttpTestingController);
    const fetcher = createMetricFetcher<{ totalCount: number }>(http, '/api/tenants/metrics/foo');

    fetcher.load();

    httpMock.expectOne('/api/tenants/metrics/foo').flush({ totalCount: 1 });
    httpMock.verify();
  });
});
