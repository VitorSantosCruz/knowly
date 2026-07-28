import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { DashboardWrapperPageComponent } from './dashboard-wrapper-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('DashboardWrapperPageComponent', () => {
  let fixture: ComponentFixture<DashboardWrapperPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardWrapperPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardWrapperPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flushEmptyGlobalMetrics(): void {
    httpMock.expectOne('/api/staff/metrics/global').flush({
      tenantCount: 0,
      newTenantsThisMonth: 0,
      articlesReadTotal: 0,
      staffCount: 0,
    });
  }

  /** DashboardPageComponent (unchanged, reused as-is) triggers a handful of its own
   * metric/timeseries requests; this test only cares about which child the wrapper renders,
   * not those children's own internals (already covered by their own specs), so drain
   * whatever is still pending with empty-shaped responses. */
  function flushAllPending(): void {
    for (const req of httpMock.match(() => true)) {
      req.flush({ days: [], articles: [], activeCount: 0, inactiveCount: 0 });
    }
  }

  it('shows a loading state while the active tenant has not resolved', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="loading-state"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-dashboard-page')).toBeFalsy();
    expect(fixture.nativeElement.querySelector('app-global-dashboard-page')).toBeFalsy();

    httpMock.expectOne('/api/tenants/memberships').flush([]);
  });

  it('renders DashboardPageComponent when an active tenant is resolved', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/tenants/memberships')
      .flush([{ tenantId: 7, tenantName: 'Acme', role: 'ADMIN', active: true }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-dashboard-page')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-global-dashboard-page')).toBeFalsy();

    flushAllPending();
  });

  it('renders GlobalDashboardPageComponent when no active tenant is resolved (staff)', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/tenants/memberships').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-global-dashboard-page')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('app-dashboard-page')).toBeFalsy();

    flushEmptyGlobalMetrics();
  });
});
