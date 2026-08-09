import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatGroupVisibility } from '../../core/chat.model';

const CLASSES: Record<ChatGroupVisibility, string> = {
  PRIVATE: 'bg-ink-100 text-ink-600 dark:bg-ink-800 dark:text-ink-400',
  REQUEST_TO_JOIN: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
  PUBLIC: 'bg-signal-100 text-signal-800 dark:bg-signal-900/30 dark:text-signal-400',
};

const KEYS: Record<ChatGroupVisibility, string> = {
  PRIVATE: 'chat.groupVisibility.private',
  REQUEST_TO_JOIN: 'chat.groupVisibility.requestToJoin',
  PUBLIC: 'chat.groupVisibility.public',
};

/** REQ-26: one shared badge, reused in the Groups list, search results, and the group's own
 * header/create dialog — mirrors `ticket-status-badge.component.ts`'s enum-in/chip-out shape. */
@Component({
  selector: 'app-group-visibility-badge',
  imports: [TranslocoPipe],
  template: `
    <span
      data-testid="group-visibility-badge"
      [attr.data-visibility]="visibility()"
      class="rounded-full px-2 py-1 text-xs font-medium"
      [class]="classes()"
    >
      {{ labelKey() | transloco }}
    </span>
  `,
})
export class GroupVisibilityBadgeComponent {
  readonly visibility = input.required<ChatGroupVisibility>();

  protected readonly classes = computed(() => CLASSES[this.visibility()]);
  protected readonly labelKey = computed(() => KEYS[this.visibility()]);
}
