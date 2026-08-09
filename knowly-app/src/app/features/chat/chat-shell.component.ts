import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { NoActiveTenantStateComponent } from '../../shared/no-active-tenant-state.component';
import { ChatDirectoryComponent } from './chat-directory.component';
import { ChatSidebarComponent, ChatSection } from './chat-sidebar.component';
import { ConversationDetailComponent } from './conversation-detail.component';
import { ConversationsPageComponent } from '../conversations/conversations-page.component';
import { SupportPageComponent } from '../support/support-page.component';

type ChatRouteKind = 'directory' | 'peer' | 'support' | 'articles';

/**
 * Route: `/chat`, `/chat/:conversationId`, `/chat/support/:channelId`,
 * `/chat/articles/:conversationId` — no route guard (REQ-1/REQ-2, PLAN.md's rationale: the
 * four sections have materially different guard needs, so this shell performs its own
 * "is there an active tenant" check only for the "Base de artigos" section, mirroring
 * `SupportPageComponent`'s existing in-component dispatch pattern).
 *
 * Which of the 4 always-visible sections (People/Groups/Support/Base de artigos, REQ-2) is
 * shown is resolved from two sources, matching the redirect table in PLAN.md:
 * - `route.data['chatSection']` — set per route entry in `app.routes.ts` for the 3
 *   id-carrying routes (`peer`/`support`/`articles`); `'directory'` for the bare `/chat` path.
 * - When `chatSection` is `'directory'`, the `section` query param decides among
 *   people/groups/support/articles (defaulting to `'people'`) — this is what lets
 *   `/support`'s and `/conversations`' `redirectTo` targets (`/chat?section=support`,
 *   `/chat?section=articles`) land on the right section without their own dedicated route.
 *
 * `ConversationDetailComponent`/`SupportPageComponent`/`ConversationsPageComponent` are
 * rendered here as regular template children, not through a nested `<router-outlet>` — each
 * already reads its own id (`:conversationId`/`:channelId`) directly off its own injected
 * `ActivatedRoute` (the same instance Angular's router attaches to this shell for the matched
 * route), exactly as `SupportPageComponent` already does for `/support`/`/support/:channelId`
 * today — no id-forwarding wiring is needed at this level.
 */
@Component({
  selector: 'app-chat-shell',
  imports: [
    ChatSidebarComponent,
    ChatDirectoryComponent,
    ConversationDetailComponent,
    SupportPageComponent,
    ConversationsPageComponent,
    NoActiveTenantStateComponent,
  ],
  template: `
    <div data-testid="chat-shell" class="page-shell grid gap-6 md:grid-cols-[240px_1fr]">
      <app-chat-sidebar
        [activeSection]="activeSection()"
        (sectionChange)="onSectionChange($event)"
      />

      <div
        class="rounded-2xl border border-ink-200/70 bg-white p-4 dark:border-ink-800/70 dark:bg-ink-900"
      >
        @switch (activeSection()) {
          @case ('support') {
            <app-support-page />
          }
          @case ('articles') {
            @if (activeTenantService.activeTenantId() !== null) {
              <app-conversations-page />
            } @else {
              <app-no-active-tenant-state />
            }
          }
          @default {
            @if (chatRouteKind() === 'peer') {
              <app-conversation-detail />
            } @else {
              <app-chat-directory />
            }
          }
        }
      </div>
    </div>
  `,
})
export class ChatShellComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly chatRouteKind = signal<ChatRouteKind>('directory');
  private readonly querySection = signal<ChatSection>('people');

  protected readonly activeSection = computed<ChatSection>(() => {
    const kind = this.chatRouteKind();
    if (kind === 'directory') {
      return this.querySection();
    }
    return kind === 'peer' ? 'groups' : kind;
  });

  ngOnInit(): void {
    this.activeTenantService.fetch();

    this.route.data.subscribe((data) => {
      this.chatRouteKind.set((data['chatSection'] as ChatRouteKind) ?? 'directory');
    });

    this.route.queryParamMap.subscribe((params) => {
      const value = params.get('section');
      this.querySection.set(
        value === 'groups' || value === 'support' || value === 'articles' ? value : 'people',
      );
    });
  }

  protected onSectionChange(section: ChatSection): void {
    this.router.navigate(['/chat'], { queryParams: { section } });
  }
}
