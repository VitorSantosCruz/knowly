import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { map } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideUser, LucideLogOut } from '@lucide/angular';
import { buttonClass } from './button-classes';
import { AuthService } from '../core/auth.service';
import { ProfileService } from '../core/profile.service';

@Component({
  selector: 'app-avatar-menu',
  imports: [TranslocoPipe, LucideUser, LucideLogOut],
  template: `
    @if (authService.isLoggedIn()) {
      <div class="relative">
        <button
          type="button"
          [class]="toggleButtonClass"
          data-testid="avatar-menu-toggle"
          [attr.aria-expanded]="open()"
          [attr.aria-label]="'profile.myProfile' | transloco"
          [attr.title]="'profile.myProfile' | transloco"
          (click)="open.set(!open())"
        >
          @if (avatarUrl() && !imageFailed()) {
            <img
              data-testid="avatar-menu-image"
              [src]="avatarUrl()"
              alt=""
              class="h-8 w-8 rounded-full object-cover"
              (error)="imageFailed.set(true)"
            />
          } @else {
            <svg
              lucideUser
              data-testid="avatar-menu-fallback"
              class="h-8 w-8 rounded-full p-1"
              aria-hidden="true"
            ></svg>
          }
        </button>
        @if (open()) {
          <ul
            role="menu"
            class="enter-fluid absolute top-full right-0 left-auto z-20 mt-1 min-w-40 rounded-xl border border-ink-800 bg-ink-900 p-1 shadow-lg shadow-ink-950/40"
          >
            <li role="none">
              <button
                type="button"
                role="menuitem"
                data-testid="avatar-menu-my-profile"
                class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-ink-200 transition-colors duration-fast ease-fluid hover:bg-ink-800"
                (click)="goToProfile()"
              >
                <svg lucideUser class="h-4 w-4 shrink-0" aria-hidden="true"></svg>
                {{ 'profile.myProfile' | transloco }}
              </button>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                data-testid="avatar-menu-logout"
                class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-ink-200 transition-colors duration-fast ease-fluid hover:bg-ink-800"
                (click)="logout()"
              >
                <svg lucideLogOut class="h-4 w-4 shrink-0" aria-hidden="true"></svg>
                {{ 'logout.label' | transloco }}
              </button>
            </li>
          </ul>
        }
      </div>
    }
  `,
})
export class AvatarMenuComponent {
  protected readonly authService = inject(AuthService);
  private readonly profileService = inject(ProfileService);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef<HTMLElement>);

  protected readonly toggleButtonClass = buttonClass('secondary', { ghost: true, rounded: true });

  protected readonly open = signal(false);
  protected readonly imageFailed = signal(false);

  protected readonly avatarUrl = toSignal(
    this.profileService.getOwnProfile().pipe(map((profile) => profile.avatarUrl)),
    { initialValue: null },
  );

  protected goToProfile(): void {
    this.open.set(false);
    this.router.navigateByUrl('/profile');
  }

  @HostListener('document:click', ['$event.target'])
  protected onDocumentClick(target: EventTarget | null): void {
    if (this.open() && target instanceof Node && !this.elementRef.nativeElement.contains(target)) {
      this.open.set(false);
    }
  }

  protected logout(): void {
    this.authService.logout().subscribe({
      complete: () => this.router.navigateByUrl('/login'),
      error: () => this.router.navigateByUrl('/login'),
    });
  }
}
