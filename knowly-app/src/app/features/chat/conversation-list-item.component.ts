import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ConversationSummary, deriveViewerRelation } from '../../core/chat.model';

/**
 * Branches on `viewerRelation` (derived client-side, see `chat.model.ts`'s
 * `deriveViewerRelation` doc comment) — REQ-4/REQ-7/REQ-8's "looking in" badge must never
 * read as "joined" framing.
 */
@Component({
  selector: 'app-conversation-list-item',
  imports: [RouterLink, TranslocoPipe],
  template: `
    <a
      [routerLink]="['/chat', conversation().id]"
      data-testid="conversation-list-item"
      class="enter-fluid flex items-center justify-between gap-3 rounded-lg border border-ink-200/70 bg-white px-4 py-3 text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:bg-ink-900 dark:hover:bg-ink-800"
    >
      <div>
        <p class="font-medium text-ink-900 dark:text-white">
          {{
            conversation().title ??
              ('chat.list.participants'
                | transloco: { count: conversation().participantUserIds.length })
          }}
        </p>
      </div>

      @if (viewerRelation() === 'LOOKING_IN') {
        <span
          data-testid="conversation-looking-in-badge"
          [attr.aria-label]="'chat.list.lookingInAriaLabel' | transloco"
          class="rounded-full bg-amber-100 px-2 py-1 text-xs font-medium text-amber-800 dark:bg-amber-900/30 dark:text-amber-400"
        >
          {{ 'chat.list.lookingInBadge' | transloco }}
        </span>
      }
    </a>
  `,
})
export class ConversationListItemComponent {
  readonly conversation = input.required<ConversationSummary>();
  readonly currentUserId = input<number | null>(null);

  protected readonly viewerRelation = computed(() =>
    deriveViewerRelation(this.conversation().participantUserIds, this.currentUserId()),
  );
}
