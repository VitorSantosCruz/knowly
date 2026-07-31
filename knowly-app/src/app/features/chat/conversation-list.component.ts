import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatService } from '../../core/chat.service';
import { ProfileService } from '../../core/profile.service';
import { ConversationListItemComponent } from './conversation-list-item.component';

/** REQ-1/7/8: own conversations + look-ins, fetched once on init. */
@Component({
  selector: 'app-conversation-list',
  imports: [ConversationListItemComponent, RouterLink, TranslocoPipe],
  template: `
    <div data-testid="conversation-list" class="flex flex-col gap-3">
      <div class="flex items-center justify-between">
        <h2 class="font-semibold text-ink-900 dark:text-white">
          {{ 'chat.list.title' | transloco }}
        </h2>
        <a
          routerLink="/chat/new"
          data-testid="new-conversation-link"
          [attr.aria-label]="'chat.list.newConversation' | transloco"
          class="rounded-lg bg-signal-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-signal-700"
        >
          {{ 'chat.list.newConversation' | transloco }}
        </a>
      </div>

      @if (chatService.conversations().length === 0) {
        <p class="text-sm text-ink-500 dark:text-ink-400">{{ 'chat.list.empty' | transloco }}</p>
      }

      @for (conversation of chatService.conversations(); track conversation.id) {
        <app-conversation-list-item
          [conversation]="conversation"
          [currentUserId]="currentUserId()"
        />
      }
    </div>
  `,
})
export class ConversationListComponent implements OnInit {
  protected readonly chatService = inject(ChatService);
  private readonly profileService = inject(ProfileService);

  protected readonly currentUserId = signal<number | null>(null);

  ngOnInit(): void {
    this.chatService.fetchConversations();
    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));
  }
}
