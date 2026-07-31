import { Component, signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco } from '@jsverse/transloco';
import { ExportButtonComponent } from './export-button.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

@Component({
  selector: 'app-host',
  imports: [ExportButtonComponent],
  template: `<app-export-button [period]="period()" />`,
})
class HostComponent {
  readonly period = signal<'7d' | '30d' | '90d' | 'all'>('30d');
}

describe('ExportButtonComponent', () => {
  let fixture: ComponentFixture<HostComponent>;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    fixture = TestBed.createComponent(HostComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('issues a GET with responseType blob and the current period on activation', () => {
    const button: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="export-button"]',
    );
    button.click();

    const req = httpMock.expectOne(
      (request) =>
        request.url === '/api/tenants/metrics/export' && request.params.get('period') === '30d',
    );
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['a,b\n1,2']));
  });

  it('triggers a browser download on a successful response', () => {
    const createObjectURLSpy = vi.fn().mockReturnValue('blob:fake-url');
    const revokeObjectURLSpy = vi.fn();
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: createObjectURLSpy,
      revokeObjectURL: revokeObjectURLSpy,
    });
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      /* noop */
    });

    const button: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="export-button"]',
    );
    button.click();

    httpMock.expectOne('/api/tenants/metrics/export?period=30d').flush(new Blob(['a,b\n1,2']));

    expect(createObjectURLSpy).toHaveBeenCalled();
    expect(clickSpy).toHaveBeenCalled();

    clickSpy.mockRestore();
    vi.unstubAllGlobals();
  });

  it('shows an inline error on a failing export request', () => {
    const button: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="export-button"]',
    );
    button.click();

    httpMock
      .expectOne((request) => request.url === '/api/tenants/metrics/export')
      .flush(new Blob(['boom']), {
        status: 500,
        statusText: 'Server Error',
        headers: { traceparent: '00-abc123def456-0000000000000001-01' },
      });
    fixture.detectChanges();

    const errorState = fixture.nativeElement.querySelector('[data-testid="error-state"]');
    expect(errorState).toBeTruthy();
    expect(errorState.textContent).toContain('abc123def456');
  });
});
