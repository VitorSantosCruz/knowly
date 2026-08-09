import { Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ConversationDetail, ViewerRelation } from '../../core/chat.model';
import { AvatarComponent } from '../../shared/avatar.component';

@Component({
  selector: 'app-chat-header',
  imports: [TranslocoPipe, AvatarComponent],
  template: `
    <header data-testid="chat-header" class="mb-3 flex flex-col gap-1">
      <div class="flex items-center gap-2">
        <!-- REQ: header shows who/what is on the other side alongside the name. Neither
             1:1 direct chats nor groups carry a photo/image field on the wire yet
             (ConversationDetail has no avatarUrl/group image), so this always renders
             AvatarComponent's generic person/group fallback today, safe to wire up a real
             avatarUrl once a backend DTO change adds one, same gap already noted in
             chat-directory-rows.service.ts. -->
        <app-avatar [avatarUrl]="null" [kind]="avatarKind()" />
        <h1 class="font-semibold text-ink-900 dark:text-white">
          {{ detail().title ?? (participantNames().join(', ') || ('chat.list.title' | transloco)) }}
        </h1>
      </div>

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

  protected readonly avatarKind = computed(() =>
    this.detail().kind === 'PEER_GROUP' ? 'group' : 'person',
  );
}
