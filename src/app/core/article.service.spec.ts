import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ArticleService } from './article.service';

describe('ArticleService', () => {
  let service: ArticleService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ArticleService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('list() fetches the tenant articles', () => {
    service.list(1).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/articles');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('upload() posts a multipart form with title and file', () => {
    const file = new File(['content'], 'sample.pdf', { type: 'application/pdf' });

    service.upload(1, 'My title', file).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/articles');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    const body = req.request.body as FormData;
    expect(body.get('title')).toBe('My title');
    expect(body.get('file')).toBe(file);
    req.flush({ id: 1, title: 'My title', status: 'PROCESSING' });
  });

  it('getDetail() fetches an article detail', () => {
    service.getDetail(1, 2).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/articles/2');
    expect(req.request.method).toBe('GET');
    req.flush({
      id: 2,
      title: 'Title',
      text: null,
      status: 'PROCESSING',
      failureReason: null,
      originalFileUrl: 'https://example.com/file',
    });
  });

  it('update() puts the new title/text', () => {
    service.update(1, 2, 'New title', 'New text').subscribe();

    const req = httpMock.expectOne('/api/tenants/1/articles/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ title: 'New title', text: 'New text' });
    req.flush({
      id: 2,
      title: 'New title',
      text: 'New text',
      status: 'READY',
      failureReason: null,
      originalFileUrl: 'https://example.com/file',
    });
  });

  it('remove() deletes the article', () => {
    service.remove(1, 2).subscribe();

    const req = httpMock.expectOne('/api/tenants/1/articles/2');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });
});
