import { Component, input, signal } from '@angular/core';
import { LucideUser } from '@lucide/angular';

/**
 * Small, reusable round avatar — an `<img>` when `avatarUrl` is set (and hasn't failed to
 * load), falling back to a generic `LucideUser` icon otherwise. Mirrors
 * `avatar-menu.component.ts`'s existing own-avatar image/fallback pattern, extracted here so
 * every other place that needs to show *someone else's* avatar (chat-unified-ui's directory
 * rows, and likely a future conversation header) doesn't reimplement it inline.
 */
@Component({
  selector: 'app-avatar',
  imports: [LucideUser],
  template: `
    @if (avatarUrl() && !imageFailed()) {
      <img
        data-testid="avatar-image"
        [src]="avatarUrl()"
        alt=""
        class="h-8 w-8 shrink-0 rounded-full object-cover"
        (error)="imageFailed.set(true)"
      />
    } @else {
      <svg
        lucideUser
        data-testid="avatar-fallback"
        class="h-8 w-8 shrink-0 rounded-full bg-ink-100 p-1.5 text-ink-500 dark:bg-ink-800 dark:text-ink-400"
        aria-hidden="true"
      ></svg>
    }
  `,
})
export class AvatarComponent {
  readonly avatarUrl = input<string | null>(null);

  protected readonly imageFailed = signal(false);
}
