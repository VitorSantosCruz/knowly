import { Component, computed, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ConversationDetail, ViewerRelation } from '../../core/chat.model';
import { AvatarComponent } from '../../shared/avatar.component';

@Component({
  selector: 'app-chat-header',
  imports: [TranslocoPipe, AvatarComponent],
  template: `
    <header data-testid="chat-header" class="mb-3 flex flex-col gap-1">
      <!-- REQ (2026-08-09 UX follow-up): the icon+name is clickable and opens a modal with
           details (a person's profile for 1:1, or the group's info/administration for
           PEER_GROUP) instead of the old always-visible inline panel — see
           person-info-modal.component.ts/group-info-modal.component.ts, hosted by
           conversation-detail.component.ts. Neither 1:1 direct chats nor groups carry a
           photo/image field on the wire yet (ConversationDetail has no avatarUrl/group image),
           so the avatar always renders AvatarComponent's generic person/group fallback today,
           same gap already noted in chat-directory-rows.service.ts. -->
      @let title =
        detail().title ?? (participantNames().join(', ') || ('chat.list.title' | transloco));
      <button
        type="button"
        data-testid="chat-header-open-info"
        [attr.aria-label]="'chat.header.openInfoAriaLabel' | transloco: { title }"
        (click)="openInfo.emit()"
        class="-mx-2 -my-1 flex w-fit cursor-pointer items-center gap-2 rounded-lg px-2 py-1 text-left transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:hover:bg-ink-800"
      >
        <app-avatar [avatarUrl]="null" [kind]="avatarKind()" />
        <h1 class="font-semibold text-ink-900 dark:text-white">
          {{ title }}
        </h1>
      </button>

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
  readonly openInfo = output<void>();

  protected readonly participantNames = computed(() =>
    Object.values(this.detail().participantNicknames),
  );

  protected readonly avatarKind = computed(() =>
    this.detail().kind === 'PEER_GROUP' ? 'group' : 'person',
  );
}
