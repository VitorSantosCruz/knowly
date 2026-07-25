import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ConfigService } from './config.service';

describe('ConfigService', () => {
  let service: ConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('defaults to an empty Turnstile site key before loading', () => {
    expect(service.turnstileSiteKey).toBe('');
  });

  it('fetches /config.json and exposes its values once loaded', async () => {
    const loadPromise = service.load();

    const req = httpMock.expectOne('/config.json');
    expect(req.request.method).toBe('GET');
    req.flush({ turnstileSiteKey: 'real-site-key' });

    await loadPromise;

    expect(service.turnstileSiteKey).toBe('real-site-key');
  });

  it('keeps the default value if config.json is missing or invalid', async () => {
    const loadPromise = service.load();

    const req = httpMock.expectOne('/config.json');
    req.flush('not found', { status: 404, statusText: 'Not Found' });

    await loadPromise;

    expect(service.turnstileSiteKey).toBe('');
  });
});
