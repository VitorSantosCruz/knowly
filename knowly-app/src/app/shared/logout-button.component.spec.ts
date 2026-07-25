import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { LogoutButtonComponent } from './logout-button.component';
import { AuthService } from '../core/auth.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('LogoutButtonComponent', () => {
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LogoutButtonComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTransloco({
          config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
          loader: FakeTranslocoLoader,
        }),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('is hidden when not logged in', () => {
    const fixture = TestBed.createComponent(LogoutButtonComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button')).toBeFalsy();
  });

  it('is shown once logged in, and logs out + navigates to /login on click', () => {
    authService.verifyCode('user@example.com', '123456').subscribe();
    httpMock.expectOne('/api/auth/login-code/verify').flush({});

    const fixture = TestBed.createComponent(LogoutButtonComponent);
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button).toBeTruthy();

    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    button.click();

    httpMock.expectOne('/api/auth/logout').flush({});
    fixture.detectChanges();

    expect(authService.isLoggedIn()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });
});
