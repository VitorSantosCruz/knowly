import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { OnboardingService } from './onboarding.service';

describe('OnboardingService', () => {
  let service: OnboardingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OnboardingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('fetch', () => {
    it('sets completed to true when the backend says so', () => {
      service.fetch();

      const req = httpMock.expectOne('/api/users/me/onboarding-status');
      expect(req.request.method).toBe('GET');
      req.flush({ completed: true });

      expect(service.completed()).toBe(true);
    });

    it('sets completed to false when the backend says so', () => {
      service.fetch();

      const req = httpMock.expectOne('/api/users/me/onboarding-status');
      req.flush({ completed: false });

      expect(service.completed()).toBe(false);
    });

    it('starts with completed as null before the fetch resolves', () => {
      expect(service.completed()).toBeNull();
    });
  });

  describe('markComplete', () => {
    it('posts to the onboarding-complete endpoint and sets completed to true', () => {
      service.markComplete().subscribe();

      const req = httpMock.expectOne('/api/users/me/onboarding-complete');
      expect(req.request.method).toBe('POST');
      req.flush({});

      expect(service.completed()).toBe(true);
    });
  });
});
