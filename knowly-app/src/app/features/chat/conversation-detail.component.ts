import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { interval } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatGroupService } from '../../core/chat-group.service';
import { ChatService } from '../../core/chat.service';
import { deriveViewerRelation } from '../../core/chat.model';
import { ProfileService } from '../../core/profile.service';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MessageThreadComponent } from '../../shared/chat/message-thread.component';
import { ChatHeaderComponent } from './chat-header.component';
import { GroupAdminPanelComponent } from './group-admin-panel.component';

const POLL_INTERVAL_MS = 5000;

/**
 * Route: `/chat/:conversationId`. REQ-4/5/6/7/8/9/19/20/21 — composer omitted entirely for a
 * `viewerRelation === 'LOOKING_IN'` look-in viewer (out-of-scope composer for oversight-only
 * presence), polling gated by `document.visibilityState`.
 */
@Component({
  selector: 'app-conversation-detail',
  imports: [
    ChatHeaderComponent,
    MessageThreadComponent,
    NoAccessStateComponent,
    GroupAdminPanelComponent,
    TranslocoPipe,
  ],
  template: `
    @if (chatService.detailErrors().has(conversationId())) {
      <app-no-access-state />
    } @else if (detail(); as detail) {
      <app-chat-header [detail]="detail" [viewerRelation]="viewerRelation()!" />

      <!-- REQ-16: a genuine participant, never a LOOKING_IN oversight-only viewer. -->
      @if (detail.kind === 'PEER_GROUP' && isGenuineParticipant()) {
        <div class="mb-3">
          @if (confirmingLeave()) {
            <button
              type="button"
              data-testid="confirm-leave-group"
              (click)="confirmLeave()"
              class="rounded-lg bg-red-600 px-3 py-1.5 text-sm font-medium text-white"
            >
              {{ 'common.confirm' | transloco }}
            </button>
          } @else {
            <button
              type="button"
              data-testid="leave-group"
              [attr.aria-label]="'chat.adminPanel.leaveGroup' | transloco"
              (click)="confirmingLeave.set(true)"
              class="rounded-lg px-3 py-1.5 text-sm font-medium text-red-600"
            >
              {{ 'chat.adminPanel.leaveGroup' | transloco }}
            </button>
          }
          @if (leaveError()) {
            <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
              {{ 'chat.adminPanel.actionError' | transloco }}
            </p>
          }
        </div>
      }

      @if (detail.kind === 'PEER_GROUP') {
        <app-group-admin-panel
          [detail]="detail"
          [currentUserId]="currentUserId()"
          (groupDeleted)="router.navigate(['/chat'])"
        />
      }

      <app-message-thread
        [messages]="entry().messages"
        [hasMore]="entry().hasMore"
        [loading]="entry().loading"
        [loadError]="entry().loadError"
        [showComposer]="viewerRelation() === 'PARTICIPANT'"
        (loadMore)="chatService.loadOlderMessages(conversationId())"
        (send)="onSend($event)"
        (retry)="onRetry($event)"
      />
    }
  `,
})
export class ConversationDetailComponent implements OnInit {
  protected readonly chatService = inject(ChatService);
  private readonly chatGroupService = inject(ChatGroupService);
  private readonly profileService = inject(ProfileService);
  private readonly route = inject(ActivatedRoute);
  protected readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  protected readonly conversationId = signal(0);
  protected readonly currentUserId = signal<number | null>(null);
  protected readonly confirmingLeave = signal(false);
  protected readonly leaveError = signal(false);

  protected readonly detail = computed(() => this.chatService.details().get(this.conversationId()));
  protected readonly entry = computed(() => this.chatService.entryOf(this.conversationId()));
  protected readonly viewerRelation = computed(() => {
    const detail = this.detail();
    if (!detail) {
      return undefined;
    }
    return deriveViewerRelation(detail.participantUserIds, this.currentUserId());
  });

  /** REQ-16: a genuine participant, distinct from an admin present only via tenant-level
   * look-in — reuses the same `participantUserIds` check `viewerRelation` is derived from. */
  protected readonly isGenuineParticipant = computed(() => this.viewerRelation() === 'PARTICIPANT');

  ngOnInit(): void {
    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));

    this.route.paramMap.subscribe((params) => {
      const id = Number(params.get('conversationId'));
      this.conversationId.set(id);
      this.chatService.openConversation(id);
    });

    interval(POLL_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.document.visibilityState === 'visible') {
          this.chatService.pollNewMessages(this.conversationId());
        }
      });
  }

  onSend(content: string): void {
    this.chatService.sendMessage(this.conversationId(), content, crypto.randomUUID()).subscribe();
  }

  onRetry(message: { localId?: string; content: string }): void {
    if (!message.localId) {
      return;
    }
    this.chatService
      .sendMessage(this.conversationId(), message.content, message.localId)
      .subscribe();
  }

  protected confirmLeave(): void {
    this.confirmingLeave.set(false);
    this.leaveError.set(false);
    const id = this.conversationId();
    this.chatGroupService.leave(id).subscribe({
      next: () => this.router.navigate(['/chat']),
      error: () => this.leaveError.set(true),
    });
  }
}
