import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TourService } from './tour.service';

describe('TourService', () => {
  let service: TourService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TourService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify({ ignoreCancelled: true });
  });

  it('starts inactive with no steps shown', () => {
    expect(service.active()).toBe(false);
  });

  it('start() activates the tour at the first step', () => {
    service.start();

    expect(service.active()).toBe(true);
    expect(service.stepIndex()).toBe(0);
  });

  it('next() advances the step index', () => {
    service.start();
    service.next();

    expect(service.stepIndex()).toBe(1);
  });

  it('back() does not go below the first step', () => {
    service.start();
    service.back();

    expect(service.stepIndex()).toBe(0);
  });

  it('next() on the last step finishes the tour and marks onboarding complete', () => {
    service.start();
    const lastIndex = service.steps.length - 1;

    for (let i = 0; i < lastIndex; i++) {
      service.next();
    }
    service.next();

    expect(service.active()).toBe(false);
    httpMock.expectOne('/api/users/me/onboarding-complete').flush({});
  });

  it('skip() finishes the tour and marks onboarding complete', () => {
    service.start();
    service.skip();

    expect(service.active()).toBe(false);
    httpMock.expectOne('/api/users/me/onboarding-complete').flush({});
  });
});
