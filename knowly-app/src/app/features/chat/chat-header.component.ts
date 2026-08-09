import { Component, computed, inject, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucidePencil } from '@lucide/angular';
import { ChatGroupService } from '../../core/chat-group.service';
import { ConversationDetail, ViewerRelation } from '../../core/chat.model';
import { AvatarComponent } from '../../shared/avatar.component';
import { RenameFormComponent } from '../../shared/chat/rename-form.component';

@Component({
  selector: 'app-chat-header',
  imports: [TranslocoPipe, AvatarComponent, RenameFormComponent, LucidePencil],
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

      @if (renaming()) {
        <app-rename-form
          [initialTitle]="detail().title ?? ''"
          [initialIcon]="detail().icon"
          [error]="renameError()"
          (saved)="onRenameSaved($event)"
          (cancelled)="renaming.set(false)"
        />
      } @else {
        <div class="flex items-center gap-1">
          <button
            type="button"
            data-testid="chat-header-open-info"
            [attr.aria-label]="'chat.header.openInfoAriaLabel' | transloco: { title }"
            (click)="openInfo.emit()"
            class="-mx-2 -my-1 flex w-fit cursor-pointer items-center gap-2 rounded-lg px-2 py-1 text-left transition-colors duration-fast ease-fluid hover:bg-ink-100 dark:hover:bg-ink-800"
          >
            <app-avatar [avatarUrl]="null" [kind]="avatarKind()" [icon]="detail().icon" />
            <h1 class="font-semibold text-ink-900 dark:text-white">
              {{ title }}
            </h1>
          </button>

          <!-- Amendment (4), REQ-40 (final): only rendered — not merely hidden — for a group
               admin, mirroring group-admin-panel.component.ts's own "removed from the DOM
               entirely" convention for admin-only actions. -->
          @if (canRenameGroup()) {
            <button
              type="button"
              data-testid="chat-header-rename"
              [attr.aria-label]="'chat.rename.pencilAriaLabel' | transloco: { title }"
              (click)="renaming.set(true)"
              class="rounded-lg p-1 text-ink-500 hover:bg-ink-100 dark:text-ink-400 dark:hover:bg-ink-800"
            >
              <svg lucidePencil class="h-4 w-4" aria-hidden="true"></svg>
            </button>
          }
        </div>
      }

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
  private readonly chatGroupService = inject(ChatGroupService);

  readonly detail = input.required<ConversationDetail>();
  readonly viewerRelation = input.required<ViewerRelation>();
  /** Amendment (4), REQ-40 (final): needed to derive `canRenameGroup()` — the exact same
   * `adminUserIds.includes(currentUserId)` computed `group-admin-panel.component.ts` already
   * derives, reused rather than duplicated. */
  readonly currentUserId = input<number | null>(null);
  readonly openInfo = output<void>();

  protected readonly renaming = signal(false);
  protected readonly renameError = signal(false);

  /** [AppSec-added, 2026-08-09]: computed from data already available client-side
   * (`adminUserIds`), not shown optimistically and gated only by a backend 403 — this is the
   * load-bearing mechanism keeping the pencil off a non-admin's view of a group, and off every
   * 1:1/Support conversation entirely (`kind !== 'PEER_GROUP'`). */
  protected readonly canRenameGroup = computed(() => {
    const detail = this.detail();
    const currentUserId = this.currentUserId();
    return (
      detail.kind === 'PEER_GROUP' &&
      currentUserId !== null &&
      detail.adminUserIds.includes(currentUserId)
    );
  });

  protected onRenameSaved(event: { title: string; icon: ConversationDetail['icon'] }): void {
    this.renameError.set(false);
    this.chatGroupService.rename(this.detail().id, event.title, event.icon ?? undefined).subscribe({
      next: () => this.renaming.set(false),
      error: () => this.renameError.set(true),
    });
  }

  protected readonly participantNames = computed(() =>
    Object.values(this.detail().participantNicknames),
  );

  protected readonly avatarKind = computed(() =>
    this.detail().kind === 'PEER_GROUP' ? 'group' : 'person',
  );
}
