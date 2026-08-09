import { Component, input, signal } from '@angular/core';
import { LucideUser, LucideUsersRound } from '@lucide/angular';
import { IconKey } from '../core/chat.model';
import { ChatIconComponent } from './chat/chat-icon.component';

/** Which generic fallback icon to show when there's no `avatarUrl` — a person (`LucideUser`,
 * the default), a group with no image of its own (`LucideUsersRound`), or a RAG "Base de
 * artigos" conversation (`LucideUsersRound` too, same generic fallback — see `icon` below for
 * the Amendment (4) per-conversation override). */
export type AvatarKind = 'person' | 'group';

/**
 * Small, reusable round avatar — an `<img>` when `avatarUrl` is set (and hasn't failed to
 * load), falling back to a generic `kind`-appropriate Lucide icon otherwise. Mirrors
 * `avatar-menu.component.ts`'s existing own-avatar image/fallback pattern, extracted here so
 * every other place that needs to show *someone else's* avatar (chat-unified-ui's directory
 * rows, conversation header, etc.) doesn't reimplement it inline.
 *
 * **Amendment (4), REQ-39/REQ-40/REQ-13 (final round):** an optional `icon` (`IconKey | null`)
 * takes priority over both the image and the generic `kind` fallback when set — rendered via
 * the shared `ChatIconComponent` (`shared/chat/chat-icon.component.ts`), same lookup every other
 * icon-rendering surface (`icon-picker.component.ts`) uses. `null`/unset behaves exactly as
 * before this amendment (falls through to the existing image/`kind`-fallback logic).
 */
@Component({
  selector: 'app-avatar',
  imports: [LucideUser, LucideUsersRound, ChatIconComponent],
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
    } @else if (icon()) {
      <span
        data-testid="avatar-icon"
        class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-ink-100 p-2 text-ink-500 dark:bg-ink-800 dark:text-ink-400"
      >
        <app-chat-icon [icon]="icon()" />
      </span>
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
  readonly icon = input<IconKey | null>(null);

  protected readonly imageFailed = signal(false);
}
