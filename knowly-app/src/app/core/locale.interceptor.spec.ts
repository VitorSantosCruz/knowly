import { HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { localeInterceptor } from './locale.interceptor';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('localeInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
  });

  it('adds an Accept-Language header matching the active language', () => {
    const request = new HttpRequest('GET', '/api/tenants/memberships');
    let seenRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (req) => {
      seenRequest = req;
      return of({ ok: true }) as never;
    };

    TestBed.runInInjectionContext(() => {
      localeInterceptor(request, next).subscribe();
    });

    expect(seenRequest?.headers.get('Accept-Language')).toBe('en');
  });

  it('reflects a changed active language on the next request', () => {
    const transloco = TestBed.inject(TranslocoService);
    transloco.setActiveLang('pt-BR');

    const request = new HttpRequest('GET', '/api/tenants/memberships');
    let seenRequest: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (req) => {
      seenRequest = req;
      return of({ ok: true }) as never;
    };

    TestBed.runInInjectionContext(() => {
      localeInterceptor(request, next).subscribe();
    });

    expect(seenRequest?.headers.get('Accept-Language')).toBe('pt-BR');
  });
});
