import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ConversationDetail, ViewerRelation } from '../../core/chat.model';

@Component({
  selector: 'app-chat-header',
  imports: [TranslocoPipe],
  template: `
    <header data-testid="chat-header" class="mb-3 flex flex-col gap-1">
      <h1 class="font-semibold text-ink-900 dark:text-white">
        {{ detail().title ?? (participantNames().join(', ') || ('chat.list.title' | transloco)) }}
      </h1>

      @if (viewerRelation() === 'LOOKING_IN') {
        <p
          data-testid="chat-header-looking-in-banner"
          role="note"
          [attr.aria-label]="'chat.header.lookingInAriaLabel' | transloco"
          class="rounded-lg bg-amber-100 px-3 py-2 text-xs text-amber-800 dark:bg-amber-900/30 dark:text-amber-400"
        >
          {{ 'chat.header.lookingInBanner' | transloco }}
        </p>
      }
    </header>
  `,
})
export class ChatHeaderComponent {
  readonly detail = input.required<ConversationDetail>();
  readonly viewerRelation = input.required<ViewerRelation>();

  protected readonly participantNames = computed(() =>
    Object.values(this.detail().participantNicknames),
  );
}
