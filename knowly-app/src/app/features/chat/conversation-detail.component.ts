import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { interval } from 'rxjs';
import { ChatService } from '../../core/chat.service';
import { deriveViewerRelation } from '../../core/chat.model';
import { ProfileService } from '../../core/profile.service';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { MessageThreadComponent } from '../../shared/chat/message-thread.component';
import { ChatHeaderComponent } from './chat-header.component';
import { GroupInfoModalComponent } from './group-info-modal.component';
import { PersonInfoModalComponent } from './person-info-modal.component';

const POLL_INTERVAL_MS = 5000;

/**
 * Route: `/chat/:conversationId`. REQ-4/5/6/7/8/9/19/20/21 — composer omitted entirely for a
 * `viewerRelation === 'LOOKING_IN'` look-in viewer (out-of-scope composer for oversight-only
 * presence), polling gated by `document.visibilityState`.
 *
 * **2026-08-09 UX follow-up**: the "who/what is this conversation with" details (a 1:1
 * participant's profile, or a group's full administration panel — visibility/members/
 * invite/delete/leave) used to sit always-visible under the header. They now live behind the
 * header's own icon+name click, in one of `PersonInfoModalComponent`/`GroupInfoModalComponent`
 * (see those files) — this component only tracks which one is open and passes through the data
 * they need; the message thread stays exactly where it was.
 */
@Component({
  selector: 'app-conversation-detail',
  imports: [
    ChatHeaderComponent,
    MessageThreadComponent,
    NoAccessStateComponent,
    PersonInfoModalComponent,
    GroupInfoModalComponent,
  ],
  template: `
    @if (chatService.detailErrors().has(conversationId())) {
      <app-no-access-state />
    } @else if (detail(); as detail) {
      <app-chat-header
        [detail]="detail"
        [viewerRelation]="viewerRelation()!"
        [currentUserId]="currentUserId()"
        (openInfo)="infoModalOpen.set(true)"
      />

      @if (detail.kind === 'PEER_GROUP') {
        <app-group-info-modal
          [open]="infoModalOpen()"
          [detail]="detail"
          [currentUserId]="currentUserId()"
          (dismissed)="infoModalOpen.set(false)"
        />
      } @else {
        <app-person-info-modal
          [open]="infoModalOpen()"
          [userId]="otherParticipantId()"
          (dismissed)="infoModalOpen.set(false)"
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
  private readonly profileService = inject(ProfileService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly document = inject(DOCUMENT);

  protected readonly conversationId = signal(0);
  protected readonly currentUserId = signal<number | null>(null);
  protected readonly infoModalOpen = signal(false);

  protected readonly detail = computed(() => this.chatService.details().get(this.conversationId()));
  protected readonly entry = computed(() => this.chatService.entryOf(this.conversationId()));
  protected readonly viewerRelation = computed(() => {
    const detail = this.detail();
    if (!detail) {
      return undefined;
    }
    return deriveViewerRelation(detail.participantUserIds, this.currentUserId());
  });

  /** The other side of a 1:1 conversation, for `PersonInfoModalComponent`'s `userId` input —
   * `null` while `currentUserId()` hasn't resolved yet, or (defensively) if this is ever called
   * for something other than an exactly-2-participant PEER_DIRECT conversation. */
  protected readonly otherParticipantId = computed(() => {
    const detail = this.detail();
    const currentUserId = this.currentUserId();
    if (!detail || currentUserId === null) {
      return null;
    }
    return detail.participantUserIds.find((id) => id !== currentUserId) ?? null;
  });

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
}
