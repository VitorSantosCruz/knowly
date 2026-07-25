import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { WelcomePageComponent } from './welcome-page.component';
import { TourService } from '../../core/tour.service';
import { FakeTranslocoLoader } from '../../testing/fake-transloco-loader';

describe('WelcomePageComponent', () => {
  let fixture: ComponentFixture<WelcomePageComponent>;
  let httpMock: HttpTestingController;
  let tourService: TourService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WelcomePageComponent],
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

    fixture = TestBed.createComponent(WelcomePageComponent);
    httpMock = TestBed.inject(HttpTestingController);
    tourService = TestBed.inject(TourService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  function flush(options: {
    memberships?: {
      tenantId: number;
      tenantName: string;
      role: 'ADMIN' | 'MEMBER';
      active: boolean;
    }[];
    onboardingCompleted?: boolean;
  }): void {
    httpMock
      .expectOne('/api/users/me/onboarding-status')
      .flush({ completed: options.onboardingCompleted ?? true });
    httpMock.expectOne('/api/tenants/memberships').flush(options.memberships ?? []);
  }

  it('shows a staff greeting with no dashboard link when there is no active tenant', () => {
    fixture.detectChanges();
    flush({ memberships: [] });
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('[data-testid="welcome-dashboard-link"]'),
    ).toBeFalsy();
  });

  it('shows a tenant-branded greeting and a dashboard link when there is an active tenant', () => {
    fixture.detectChanges();
    flush({
      memberships: [{ tenantId: 1, tenantName: 'Acme', role: 'MEMBER', active: true }],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Acme');
    expect(
      fixture.nativeElement.querySelector('[data-testid="welcome-dashboard-link"]'),
    ).toBeTruthy();
  });

  it('starts the tour automatically when onboarding is not yet completed', () => {
    const startSpy = vi.spyOn(tourService, 'start');
    fixture.detectChanges();
    flush({ onboardingCompleted: false });
    fixture.detectChanges();

    expect(startSpy).toHaveBeenCalled();
  });

  it('does not start the tour automatically when onboarding was already completed', () => {
    const startSpy = vi.spyOn(tourService, 'start');
    fixture.detectChanges();
    flush({ onboardingCompleted: true });
    fixture.detectChanges();

    expect(startSpy).not.toHaveBeenCalled();
  });
});
