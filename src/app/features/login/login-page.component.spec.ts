import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoLoader, provideTransloco } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';
import { LoginPageComponent } from './login-page.component';
import { AuthService } from '../../core/auth.service';

@Injectable()
class FakeTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({});
  }
}

function setup() {
  TestBed.configureTestingModule({
    imports: [LoginPageComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideTransloco({
        config: { availableLangs: ['en', 'pt-BR'], defaultLang: 'en' },
        loader: FakeTranslocoLoader,
      }),
    ],
  });

  const fixture = TestBed.createComponent(LoginPageComponent);
  fixture.detectChanges();
  return fixture;
}

describe('LoginPageComponent', () => {
  it('renders the email step with a centered email input as the primary action', () => {
    const fixture = setup();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    expect(input).toBeTruthy();
  });

  it('navigates to the credential step when the email is submitted successfully', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(of(undefined));

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    input.value = 'user@example.com';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(authService.requestLogin).toHaveBeenCalledWith('user@example.com', undefined);
    expect(fixture.nativeElement.querySelector('[data-testid="credential-step"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('input[type="email"]')).toBeFalsy();
  });

  it('stays on the email step when the request fails for a reason other than CAPTCHA_REQUIRED', () => {
    const fixture = setup();
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'requestLogin').mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    input.value = 'user@example.com';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input[type="email"]')).toBeTruthy();
  });
});
