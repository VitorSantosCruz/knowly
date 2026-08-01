import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { AvatarMenuComponent } from './avatar-menu.component';
import { AuthService } from '../core/auth.service';
import { FakeTranslocoLoader } from '../testing/fake-transloco-loader';

describe('AvatarMenuComponent', () => {
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AvatarMenuComponent],
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

  function login(): void {
    authService.verifyCode('user@example.com', '123456').subscribe();
    httpMock.expectOne('/api/auth/login-code/verify').flush({});
  }

  it('is hidden when not logged in', () => {
    const fixture = TestBed.createComponent(AvatarMenuComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-menu-toggle"]')).toBeFalsy();
  });

  it('renders the avatar image when avatarUrl is non-null', () => {
    login();
    const fixture = TestBed.createComponent(AvatarMenuComponent);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'a@b.com', fields: {}, avatarUrl: 'https://example.com/a.png' });
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-image"]',
    );
    expect(img).toBeTruthy();
    expect(img.src).toContain('https://example.com/a.png');
    expect(fixture.nativeElement.querySelector('[data-testid="avatar-menu-fallback"]')).toBeFalsy();
  });

  it('renders the fallback icon when avatarUrl is null', () => {
    login();
    const fixture = TestBed.createComponent(AvatarMenuComponent);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'a@b.com', fields: {}, avatarUrl: null });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-menu-image"]')).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="avatar-menu-fallback"]'),
    ).toBeTruthy();
  });

  it('renders the fallback icon after the image fails to load', () => {
    login();
    const fixture = TestBed.createComponent(AvatarMenuComponent);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'a@b.com', fields: {}, avatarUrl: 'https://example.com/a.png' });
    fixture.detectChanges();

    const img: HTMLImageElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-image"]',
    );
    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="avatar-menu-image"]')).toBeFalsy();
    expect(
      fixture.nativeElement.querySelector('[data-testid="avatar-menu-fallback"]'),
    ).toBeTruthy();
  });

  function createLoggedIn(): ReturnType<typeof TestBed.createComponent<AvatarMenuComponent>> {
    login();
    const fixture = TestBed.createComponent(AvatarMenuComponent);
    fixture.detectChanges();
    httpMock
      .expectOne('/api/users/me/profile')
      .flush({ userId: 1, email: 'a@b.com', fields: {}, avatarUrl: null });
    fixture.detectChanges();
    return fixture;
  }

  it('clicking the trigger opens a menu with exactly two entries in order, each with an icon', () => {
    const fixture = createLoggedIn();

    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-toggle"]',
    );
    expect(trigger.getAttribute('aria-label')).toBeTruthy();

    trigger.click();
    fixture.detectChanges();

    const menu = fixture.nativeElement.querySelector('[role="menu"]');
    expect(menu).toBeTruthy();

    const items: NodeListOf<HTMLElement> =
      fixture.nativeElement.querySelectorAll('[role="menuitem"]');
    expect(items.length).toBe(2);
    expect(items[0].getAttribute('data-testid')).toBe('avatar-menu-my-profile');
    expect(items[0].querySelector('svg')).toBeTruthy();
    expect(items[1].getAttribute('data-testid')).toBe('avatar-menu-logout');
    expect(items[1].querySelector('svg')).toBeTruthy();
  });

  it('selecting "My profile" navigates to /profile and closes the menu', () => {
    const fixture = createLoggedIn();
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-toggle"]',
    );
    trigger.click();
    fixture.detectChanges();

    const myProfile: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-my-profile"]',
    );
    myProfile.click();
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith('/profile');
    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeFalsy();
  });

  it('selecting "Logout" calls AuthService.logout() and navigates to /login on success', () => {
    const fixture = createLoggedIn();
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-toggle"]',
    );
    trigger.click();
    fixture.detectChanges();

    const logout: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-logout"]',
    );
    logout.click();

    httpMock.expectOne('/api/auth/logout').flush({});
    fixture.detectChanges();

    expect(authService.isLoggedIn()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });

  it('selecting "Logout" still navigates to /login when the logout call errors', () => {
    const fixture = createLoggedIn();
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');

    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-toggle"]',
    );
    trigger.click();
    fixture.detectChanges();

    const logout: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="avatar-menu-logout"]',
    );
    logout.click();

    httpMock
      .expectOne('/api/auth/logout')
      .flush({}, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(navigateSpy).toHaveBeenCalledWith('/login');
  });
});
