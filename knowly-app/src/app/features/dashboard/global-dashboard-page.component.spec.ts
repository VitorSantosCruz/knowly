import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco } from '@jsverse/transloco';
import { GlobalDashboardPageComponent } from './global-dashboard-page.component';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('GlobalDashboardPageComponent', () => {
  let fixture: ComponentFixture<GlobalDashboardPageComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GlobalDashboardPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(GlobalDashboardPageComponent);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('renders 4 populated tiles plus 1 disabled tile after a successful fetch', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/staff/metrics/global').flush({
      tenantCount: 12,
      newTenantsThisMonth: 3,
      articlesReadTotal: 999,
      staffCount: 7,
    });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="tenant-count-tile"]').textContent,
    ).toContain('12');
    expect(
      fixture.nativeElement.querySelector('[data-testid="new-tenants-tile"]').textContent,
    ).toContain('3');
    expect(
      fixture.nativeElement.querySelector('[data-testid="articles-read-tile"]').textContent,
    ).toContain('999');
    expect(
      fixture.nativeElement.querySelector('[data-testid="staff-count-tile"]').textContent,
    ).toContain('7');
    expect(
      fixture.nativeElement
        .querySelector('[data-testid="support-tickets-tile"]')
        .querySelector('[data-testid="metric-tile-coming-soon"]'),
    ).toBeTruthy();
  });

  it('renders app-no-access-state once at the page level on a 403', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/metrics/global')
      .flush({ code: 'PERMISSION_DENIED' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('[data-testid="no-access-state"]').length).toBe(
      1,
    );
    expect(fixture.nativeElement.querySelector('[data-testid="tenant-count-tile"]')).toBeFalsy();
  });

  it('renders app-error-state on a non-403 error', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/staff/metrics/global')
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="error-state"]')).toBeTruthy();
  });
});
