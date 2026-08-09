import { Component, input, signal } from '@angular/core';
import { LucideUser, LucideUsersRound } from '@lucide/angular';

/** Which generic fallback icon to show when there's no `avatarUrl` — a person (`LucideUser`,
 * the default) or a group with no image of its own (`LucideUsersRound`). */
export type AvatarKind = 'person' | 'group';

/**
 * Small, reusable round avatar — an `<img>` when `avatarUrl` is set (and hasn't failed to
 * load), falling back to a generic `kind`-appropriate Lucide icon otherwise. Mirrors
 * `avatar-menu.component.ts`'s existing own-avatar image/fallback pattern, extracted here so
 * every other place that needs to show *someone else's* avatar (chat-unified-ui's directory
 * rows, conversation header, etc.) doesn't reimplement it inline.
 */
@Component({
  selector: 'app-avatar',
  imports: [LucideUser, LucideUsersRound],
  template: `
    @if (avatarUrl() && !imageFailed()) {
      <img
        data-testid="avatar-image"
        [src]="avatarUrl()"
        alt=""
        width="48"
        height="48"
        class="h-12 w-12 shrink-0 rounded-full object-cover"
        (error)="imageFailed.set(true)"
      />
    } @else if (kind() === 'group') {
      <svg
        lucideUsersRound
        data-testid="avatar-fallback"
        class="h-12 w-12 shrink-0 rounded-full bg-ink-100 p-2 text-ink-500 dark:bg-ink-800 dark:text-ink-400"
        aria-hidden="true"
      ></svg>
    } @else {
      <svg
        lucideUser
        data-testid="avatar-fallback"
        class="h-12 w-12 shrink-0 rounded-full bg-ink-100 p-2 text-ink-500 dark:bg-ink-800 dark:text-ink-400"
        aria-hidden="true"
      ></svg>
    }
  `,
})
export class AvatarComponent {
  readonly avatarUrl = input<string | null>(null);
  readonly kind = input<AvatarKind>('person');

  protected readonly imageFailed = signal(false);
}
