import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { SidebarStateService } from '../../core/sidebar-state.service';
import { NoActiveTenantStateComponent } from '../../shared/no-active-tenant-state.component';
import { ChatDirectoryComponent } from './chat-directory.component';
import { ChatFullDirectoryComponent } from './chat-full-directory.component';
import { ChatSidebarComponent } from './chat-sidebar.component';
import { ConversationDetailComponent } from './conversation-detail.component';
import { CreateGroupDialogComponent } from './create-group-dialog.component';
import { CreateConversationDialogComponent } from './create-conversation-dialog.component';
import { ConversationsPageComponent } from '../conversations/conversations-page.component';
import { SupportPageComponent } from '../support/support-page.component';

type ChatRouteKind = 'directory' | 'peer' | 'support' | 'articles';
type QuerySection = 'people' | 'groups' | 'support' | 'articles';

/**
 * UX fix (2026-08-10, reported by a tester): 3 fixed-280px-plus-content columns need real
 * room — `SidebarStateService.viewportIsDesktop()`'s shared 768px breakpoint (tuned for the
 * app-wide single collapsible nav rail) let the 3-column grid try to render well before it
 * actually fit, causing horizontal+vertical scroll everywhere, then (after raising this to
 * 1024px) an uncomfortably cramped middle column with real conversation content well past that
 * too — short messages wrapping across 5–6 lines. This is a dedicated, wider breakpoint just for
 * chat's own column collapse, independent of the shared sidebar rail's threshold (unaffected, so
 * other routes keep their existing behavior).
 */
const CHAT_COLUMNS_QUERY = '(min-width: 1280px)';

/**
 * Route: `/chat`, `/chat/:conversationId`, `/chat/support/:channelId`,
 * `/chat/articles/:conversationId` — no route guard (REQ-1/REQ-2, PLAN.md's rationale).
 *
 * **Amendment (3), 2026-08-09**: this shell lays out **3** persistent columns — a
 * conversations column (`ChatSidebarComponent`'s 3 direct actions + `ChatDirectoryComponent`'s
 * one unified, Support-pinned list), a conversation/thread column showing whichever
 * conversation/Support/RAG view is currently open, and a full-directory column
 * (`ChatFullDirectoryComponent`, REQ-2d) listing everyone/every group not already in column 1 —
 * the first and third the same width, using the exact same unchanged conversation-column
 * components as before (`ConversationDetailComponent`/`SupportPageComponent`/
 * `ConversationsPageComponent`).
 *
 * History: (1) the originally-shipped tab-strip design (entire main panel swapped per section);
 * (2) a 3-column cut with a separate `ChatContactsPanelComponent` "já falou"/"ainda não falou"
 * column, retired same-day for being redundant with column 1's own People rows; (3) a 2-column
 * cut that folded that partition into column 1 directly; (4) this Amendment (3) 3-column cut,
 * which unifies column 1 into one list and reintroduces a genuine, disjoint column 3. Below the
 * 3-column breakpoint, the shell collapses to **one of the three columns at a time** (REQ-2c,
 * Amended (3), final), reusing `SidebarStateService.viewportIsDesktop()` — the same breakpoint
 * signal `app-shell.component.ts` already uses for its own collapsible-sidebar convention. A
 * `mobileFullDirectoryOpen` signal (reset whenever the route itself changes, e.g. opening a
 * conversation from either directory column) tracks whether the viewer last activated column 3
 * over column 1 — the third pane is otherwise never route-addressable on its own, unlike the
 * conversation column.
 *
 * Which view opens in the conversation column is still resolved from the same two sources as
 * before:
 * - `route.data['chatSection']` — set per route entry in `app.routes.ts` for the 3
 *   id-carrying routes (`peer`/`support`/`articles`); `'directory'` for the bare `/chat` path.
 * - When `chatSection` is `'directory'`, the `section` query param decides among
 *   people/groups/support/articles (defaulting to `'people'`) — people/groups have no
 *   dedicated conversation-column content (nothing specific is open yet), so that column shows
 *   a neutral placeholder in that case.
 */
@Component({
  selector: 'app-chat-shell',
  imports: [
    TranslocoPipe,
    ChatSidebarComponent,
    ChatDirectoryComponent,
    ChatFullDirectoryComponent,
    ConversationDetailComponent,
    SupportPageComponent,
    ConversationsPageComponent,
    NoActiveTenantStateComponent,
    CreateGroupDialogComponent,
    CreateConversationDialogComponent,
  ],
  template: `
    <div
      data-testid="chat-shell"
      class="page-shell grid h-full min-h-0 grid-rows-1 gap-4 overflow-hidden xl:grid-cols-[280px_1fr_280px]"
    >
      @if (viewportFitsColumns() || mobileView() === 'directory') {
        <div
          data-testid="chat-shell-directory-column"
          class="flex h-full min-h-0 flex-col gap-4 rounded-2xl border border-ink-200/70 bg-white p-4 dark:border-ink-800/70 dark:bg-ink-900"
        >
          <div class="flex shrink-0 flex-col gap-4">
            <app-chat-sidebar
              [hasActiveTenant]="hasActiveTenantForSidebar()"
              (openArticles)="onOpenArticles()"
              (createGroup)="createGroupOpen.set(true)"
            />
            @if (!viewportFitsColumns()) {
              <button
                type="button"
                data-testid="chat-shell-mobile-browse-directory"
                [attr.aria-label]="'chat.shell.toggleContacts' | transloco"
                (click)="mobileFullDirectoryOpen.set(true)"
                class="text-sm font-medium text-signal-700 dark:text-signal-300"
              >
                {{ 'chat.shell.toggleContacts' | transloco }}
              </button>
            }
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto">
            <app-chat-directory />
          </div>
        </div>
      }

      @if (viewportFitsColumns() || mobileView() === 'conversation') {
        <div
          data-testid="chat-shell-conversation-column"
          class="flex h-full min-h-0 flex-col rounded-2xl border border-ink-200/70 bg-white p-4 dark:border-ink-800/70 dark:bg-ink-900"
        >
          @if (!viewportFitsColumns()) {
            <button
              type="button"
              data-testid="chat-shell-mobile-back"
              [attr.aria-label]="'chat.shell.backToDirectory' | transloco"
              (click)="onBackToDirectory()"
              class="mb-3 shrink-0 text-sm font-medium text-signal-700 dark:text-signal-300"
            >
              {{ 'chat.shell.backToDirectory' | transloco }}
            </button>
          }

          <div class="flex min-h-0 flex-1 flex-col">
            @switch (activeSection()) {
              @case ('support') {
                <app-support-page />
              }
              @case ('articles') {
                @if (!activeTenantService.activeTenantResolved()) {
                  <p data-testid="chat-shell-articles-loading-state" class="text-sm text-ink-400">
                    …
                  </p>
                } @else if (activeTenantService.activeTenantId() !== null) {
                  <app-conversations-page />
                } @else {
                  <app-no-active-tenant-state />
                }
              }
              @default {
                @if (chatRouteKind() === 'peer') {
                  <app-conversation-detail />
                } @else {
                  <p
                    data-testid="chat-shell-conversation-placeholder"
                    class="text-sm text-ink-500 dark:text-ink-400"
                  >
                    {{ 'chat.shell.noConversationSelected' | transloco }}
                  </p>
                }
              }
            }
          </div>
        </div>
      }

      @if (viewportFitsColumns() || mobileView() === 'fullDirectory') {
        <div
          data-testid="chat-shell-full-directory-column"
          class="flex h-full min-h-0 flex-col gap-4 rounded-2xl border border-ink-200/70 bg-white p-4 dark:border-ink-800/70 dark:bg-ink-900"
        >
          @if (!viewportFitsColumns()) {
            <button
              type="button"
              data-testid="chat-shell-mobile-back"
              [attr.aria-label]="'chat.shell.backToDirectory' | transloco"
              (click)="onBackToDirectory()"
              class="mb-3 shrink-0 text-sm font-medium text-signal-700 dark:text-signal-300"
            >
              {{ 'chat.shell.backToDirectory' | transloco }}
            </button>
          }
          <div class="min-h-0 flex-1 overflow-y-auto">
            <app-chat-full-directory />
          </div>
        </div>
      }
    </div>

    <app-create-group-dialog [open]="createGroupOpen()" (dismissed)="createGroupOpen.set(false)" />
    <app-create-conversation-dialog
      [open]="createConversationOpen()"
      (dismissed)="createConversationOpen.set(false)"
    />
  `,
  // Angular component hosts default to display:inline with no explicit height, which breaks
  // percentage-height chains (h-full) at every component boundary — this host needs a real,
  // definite height (it sits directly under app-shell's own h-dvh/flex-1/overflow-y-auto <main>)
  // so its grid can fill the viewport and let each column scroll internally instead of the whole
  // page scrolling as one block.
  styles: [':host { display: block; height: 100%; min-height: 0; overflow: hidden; }'],
})
export class ChatShellComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);
  protected readonly sidebarState = inject(SidebarStateService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly columnsQueryList = window.matchMedia(CHAT_COLUMNS_QUERY);
  protected readonly viewportFitsColumns = signal(this.columnsQueryList.matches);

  protected readonly chatRouteKind = signal<ChatRouteKind>('directory');
  private readonly querySection = signal<QuerySection>('people');

  constructor() {
    const listener = (event: MediaQueryListEvent): void => {
      this.viewportFitsColumns.set(event.matches);
    };
    this.columnsQueryList.addEventListener('change', listener);
    inject(DestroyRef).onDestroy(() => {
      this.columnsQueryList.removeEventListener('change', listener);
    });
  }

  protected readonly createGroupOpen = signal(false);
  protected readonly createConversationOpen = signal(false);

  protected readonly activeSection = computed<QuerySection>(() => {
    const kind = this.chatRouteKind();
    if (kind === 'directory') {
      return this.querySection();
    }
    return kind === 'peer' ? 'groups' : kind;
  });

  /**
   * Bug fix (2026-08-09, reported by the product owner): on a hard reload of `/chat` while
   * inside an active tenant, `ActiveTenantService.activeTenantId()` reads `null` for the brief
   * window between this component's own `fetch()` call (below) resolving — indistinguishable
   * from the genuine "staff, no active tenant" case unless gated on `activeTenantResolved()`
   * first, exactly as `DashboardWrapperPageComponent`/`UserManagementPageComponent`/
   * `ArticlesPageComponent` already do for their own tenant-scoped views. This shell's sidebar
   * quick actions used to skip that check entirely, so the reload's loading window rendered as
   * "no active tenant" (hiding the Support/Base-de-artigos actions) before self-correcting once
   * `fetch()` resolved — easy to misread as a persistent context loss rather than a flash.
   * Assumes "has an active tenant" while unresolved (the common case) rather than "doesn't",
   * so the actions don't visibly flash hidden then reappear; once resolved, this exactly matches
   * `activeTenantId() !== null`. The `articles` case (below) uses the same
   * resolved/unresolved/none 3-way split as those other pages instead, since it renders a whole
   * different child component per branch rather than toggling a boolean input.
   */
  protected readonly hasActiveTenantForSidebar = computed(
    () =>
      !this.activeTenantService.activeTenantResolved() ||
      this.activeTenantService.activeTenantId() !== null,
  );

  /** REQ-2c (Amended (3), final): which single column is shown below the 3-column breakpoint.
   * The conversation/directory halves are still derived entirely from the current route, same
   * as before (a specific conversation/Support/RAG view means "conversation", anything else
   * means "directory") — the third state, `'fullDirectory'`, is the one exception: column 3 has
   * no route of its own to derive from, so it's the only branch driven by a manually-toggled
   * signal (`mobileFullDirectoryOpen`, reset on every route change below) rather than the URL. */
  protected readonly mobileView = computed<'directory' | 'conversation' | 'fullDirectory'>(() => {
    if (this.chatRouteKind() === 'peer') {
      return 'conversation';
    }
    const section = this.activeSection();
    if (section === 'support' || section === 'articles') {
      return 'conversation';
    }
    return this.mobileFullDirectoryOpen() ? 'fullDirectory' : 'directory';
  });

  /** REQ-2c (Amended (3), final): tracks whether the viewer last activated column 3 over
   * column 1 below the collapse breakpoint — reset to `false` on every route change (below), so
   * opening a conversation from either directory column always lands back on the conversation
   * pane, never leaving a stale "was browsing column 3" state behind. */
  protected readonly mobileFullDirectoryOpen = signal(false);

  ngOnInit(): void {
    this.activeTenantService.fetch();

    this.route.data.subscribe((data) => {
      this.chatRouteKind.set((data['chatSection'] as ChatRouteKind) ?? 'directory');
      this.mobileFullDirectoryOpen.set(false);
    });

    this.route.queryParamMap.subscribe((params) => {
      const value = params.get('section');
      this.querySection.set(
        value === 'groups' || value === 'support' || value === 'articles' ? value : 'people',
      );
      this.mobileFullDirectoryOpen.set(false);
    });
  }

  /** REQ-2's "Falar com a base de artigos" always starts a new RAG conversation (mirrors
   * `conversations` SPEC's own REQ-2) — unlike existing rows, which reopen an existing one.
   * **Amendment (4), REQ-38**: this now opens the naming dialog (name required, icon optional)
   * instead of silently creating an unnamed conversation — see
   * `create-conversation-dialog.component.ts`, which owns the actual
   * `ConversationService.create(tenantId, title, icon)` call and create-and-open behavior. With
   * no active tenant, this still falls back to the plain "articles" section navigation (no
   * tenant to create a conversation in yet). */
  protected onOpenArticles(): void {
    const tenantId = this.activeTenantService.activeTenantId();
    if (tenantId === null) {
      this.router.navigate(['/chat'], { queryParams: { section: 'articles' } });
      return;
    }
    this.createConversationOpen.set(true);
  }

  protected onBackToDirectory(): void {
    if (this.mobileFullDirectoryOpen()) {
      this.mobileFullDirectoryOpen.set(false);
      return;
    }
    this.router.navigate(['/chat']);
  }
}
