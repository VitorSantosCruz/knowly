import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  ChatDirectoryRowsService,
  GroupRow,
  PersonRow,
} from '../../core/chat-directory-rows.service';
import { AvatarComponent } from '../../shared/avatar.component';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';

/**
 * Directory column's (only column 1, in this 2-column layout — REQ-1/REQ-2) permanent
 * content — a single, always-visible list combining People, Groups, the Support row, and
 * every "Base de artigos" conversation, never hidden behind a section tab.
 *
 * **People (2026-08-09, same-day follow-up to the 3-column amendment)**: the product owner
 * found a separate 3rd "contacts" column redundant with this column's own People rows, so
 * that column's "já falou"/"ainda não falou" partitioning idea now lives *here* instead,
 * replacing the flat People list — "Already talked to" (an existing 1:1 conversation) and
 * "Haven't talked yet" (eligible, not yet messaged), each with its own independent search.
 * Groups keep a single search field (REQ-8, unchanged for Groups); Support/"Base de artigos"
 * are never filtered by any search (REQ-9).
 *
 * The actual click-to-open-or-create/join/request-to-join logic and the underlying data
 * (`ChatService`/`ChatDirectoryService`/`ChatGroupService`) live in `ChatDirectoryRowsService`.
 */
@Component({
  selector: 'app-chat-directory',
  imports: [TranslocoPipe, GroupVisibilityBadgeComponent, AvatarComponent],
  template: `
    <div data-testid="chat-directory" class="flex flex-col gap-6">
      <section>
        <h2 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
          {{ 'chat.contacts.talkedTitle' | transloco }}
        </h2>
        <label class="mb-2 flex flex-col gap-1 text-sm">
          <span class="sr-only">{{ 'chat.contacts.talkedSearchLabel' | transloco }}</span>
          <input
            type="search"
            data-testid="chat-directory-talked-search"
            [attr.aria-label]="'chat.contacts.talkedSearchLabel' | transloco"
            [value]="talkedQuery()"
            (input)="talkedQuery.set($any($event.target).value)"
            class="rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
          />
        </label>
        @if (filteredTalked().length === 0) {
          @if (talkedQuery() === '') {
            <p class="text-sm text-ink-500 dark:text-ink-400">
              {{ 'chat.directory.emptyState' | transloco }}
            </p>
          } @else {
            <p
              data-testid="chat-directory-talked-no-results"
              class="text-sm text-ink-500 dark:text-ink-400"
            >
              {{ 'chat.directory.noResults' | transloco: { query: talkedQuery() } }}
            </p>
          }
        } @else {
          <ul class="flex flex-col gap-1">
            @for (row of filteredTalked(); track row.key) {
              <li>
                <button
                  type="button"
                  [attr.data-testid]="'chat-directory-row-' + row.key"
                  [attr.aria-label]="
                    'chat.directory.personRowAriaLabel' | transloco: { nickname: row.displayName }
                  "
                  (click)="onPersonClick(row)"
                  [attr.aria-current]="isPersonRowActive(row) ? 'page' : null"
                  class="flex w-full items-center gap-2 rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="isPersonRowActive(row)"
                  [class.dark:bg-signal-900]="isPersonRowActive(row)"
                >
                  <app-avatar [avatarUrl]="row.avatarUrl" />
                  <span>{{ row.displayName }}</span>
                </button>
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              </li>
            }
          </ul>
        }
      </section>

      <section>
        <h2 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
          {{ 'chat.contacts.notTalkedTitle' | transloco }}
        </h2>
        <label class="mb-2 flex flex-col gap-1 text-sm">
          <span class="sr-only">{{ 'chat.contacts.notTalkedSearchLabel' | transloco }}</span>
          <input
            type="search"
            data-testid="chat-directory-not-talked-search"
            [attr.aria-label]="'chat.contacts.notTalkedSearchLabel' | transloco"
            [value]="notTalkedQuery()"
            (input)="notTalkedQuery.set($any($event.target).value)"
            class="rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
          />
        </label>
        @if (filteredNotTalked().length === 0) {
          @if (notTalkedQuery() === '') {
            <p data-testid="chat-directory-empty" class="text-sm text-ink-500 dark:text-ink-400">
              {{ 'chat.directory.emptyState' | transloco }}
            </p>
          } @else {
            <p
              data-testid="chat-directory-not-talked-no-results"
              class="text-sm text-ink-500 dark:text-ink-400"
            >
              {{ 'chat.directory.noResults' | transloco: { query: notTalkedQuery() } }}
            </p>
          }
        } @else {
          <ul class="flex flex-col gap-1">
            @for (row of filteredNotTalked(); track row.key) {
              <li>
                <button
                  type="button"
                  [attr.data-testid]="'chat-directory-row-' + row.key"
                  [attr.aria-label]="
                    'chat.directory.personRowAriaLabel' | transloco: { nickname: row.displayName }
                  "
                  (click)="onPersonClick(row)"
                  [attr.aria-current]="isPersonRowActive(row) ? 'page' : null"
                  class="flex w-full items-center gap-2 rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="isPersonRowActive(row)"
                  [class.dark:bg-signal-900]="isPersonRowActive(row)"
                >
                  <app-avatar [avatarUrl]="row.avatarUrl" />
                  <span>{{ row.displayName }}</span>
                </button>
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              </li>
            }
          </ul>
        }
      </section>

      <section>
        <h2 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
          {{ 'chat.directory.groupsTitle' | transloco }}
        </h2>
        <label class="mb-2 flex flex-col gap-1 text-sm">
          <span class="sr-only">{{ 'chat.directory.searchLabel' | transloco }}</span>
          <input
            type="search"
            data-testid="chat-directory-search"
            [attr.aria-label]="'chat.directory.searchLabel' | transloco"
            [value]="groupQuery()"
            (input)="groupQuery.set($any($event.target).value)"
            placeholder="{{ 'chat.directory.searchPlaceholder' | transloco }}"
            class="rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
          />
        </label>
        @if (filteredGroups().length === 0) {
          @if (groupQuery() === '') {
            <p
              data-testid="chat-directory-groups-empty"
              class="text-sm text-ink-500 dark:text-ink-400"
            >
              {{ 'chat.directory.emptyState' | transloco }}
            </p>
          } @else {
            <p
              data-testid="chat-directory-no-results"
              class="text-sm text-ink-500 dark:text-ink-400"
            >
              {{ 'chat.directory.noResults' | transloco: { query: groupQuery() } }}
            </p>
          }
        } @else {
          <ul class="flex flex-col gap-1">
            @for (row of filteredGroups(); track row.key) {
              <li>
                <button
                  type="button"
                  [attr.data-testid]="'chat-directory-row-' + row.key"
                  [attr.aria-label]="groupRowAriaLabel(row) | transloco: { title: row.displayName }"
                  [disabled]="pendingGroupIds().has(row.id)"
                  (click)="onGroupClick(row)"
                  [attr.aria-current]="row.isMember && row.id === activePeerId() ? 'page' : null"
                  class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 disabled:opacity-60 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  [class.bg-signal-50]="row.isMember && row.id === activePeerId()"
                  [class.dark:bg-signal-900]="row.isMember && row.id === activePeerId()"
                >
                  <span class="flex items-center gap-2">
                    <app-avatar kind="group" />
                    {{ row.displayName }}
                  </span>
                  @if (row.visibility) {
                    <app-group-visibility-badge [visibility]="row.visibility" />
                  }
                </button>
                @if (pendingGroupIds().has(row.id)) {
                  <p
                    data-testid="chat-directory-request-pending"
                    class="mt-1 text-xs text-ink-500 dark:text-ink-400"
                  >
                    {{ 'chat.directory.requestPending' | transloco }}
                  </p>
                }
                @if (rowErrors().has(row.key)) {
                  <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                    {{ 'chat.directory.actionError' | transloco }}
                  </p>
                }
              </li>
            }
          </ul>
        }
      </section>

      <ul class="flex flex-col gap-1">
        <li>
          <button
            type="button"
            data-testid="chat-directory-row-support"
            [attr.aria-label]="'chat.directory.supportRowAriaLabel' | transloco"
            (click)="rowsService.onSupportClick()"
            [attr.aria-current]="isSupportActive() ? 'page' : null"
            class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
            [class.bg-signal-50]="isSupportActive()"
            [class.dark:bg-signal-900]="isSupportActive()"
          >
            {{ 'chat.directory.supportRowLabel' | transloco }}
          </button>
        </li>
        @for (row of articleRows(); track row.key) {
          <li>
            <button
              type="button"
              [attr.data-testid]="'chat-directory-row-' + row.key"
              [attr.aria-label]="
                'chat.directory.articleRowAriaLabel'
                  | transloco
                    : {
                        title:
                          row.displayName ||
                          ('chat.directory.untitledArticleConversation' | transloco),
                      }
              "
              (click)="rowsService.onArticleClick(row)"
              [attr.aria-current]="row.id === activeArticleId() ? 'page' : null"
              class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
              [class.bg-signal-50]="row.id === activeArticleId()"
              [class.dark:bg-signal-900]="row.id === activeArticleId()"
            >
              {{ row.displayName || ('chat.directory.untitledArticleConversation' | transloco) }}
            </button>
          </li>
        }
      </ul>
    </div>
  `,
})
export class ChatDirectoryComponent implements OnInit {
  protected readonly rowsService = inject(ChatDirectoryRowsService);
  private readonly router = inject(Router);

  /** UX fix (2026-08-09): the currently-open conversation/group/article/Support row gets a
   * visual "active" state (mirrors `nav-menu.component.ts`'s active-nav-link convention),
   * derived purely from the current URL — never from anything that changes on click alone, so
   * this can't itself cause the reordering flicker a tester reported (see
   * `ChatDirectoryRowsService`'s own doc comment on why sort order never reacts to selection). */
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  protected readonly activePeerId = computed(() => {
    const match = /^\/chat\/(\d+)(?:\?|$)/.exec(this.currentUrl());
    return match ? Number(match[1]) : null;
  });

  protected readonly activeArticleId = computed(() => {
    const match = /^\/chat\/articles\/(\d+)/.exec(this.currentUrl());
    return match ? Number(match[1]) : null;
  });

  protected isSupportActive(): boolean {
    const url = this.currentUrl();
    return url.startsWith('/chat/support') || url.includes('section=support');
  }

  protected isPersonRowActive(row: PersonRow): boolean {
    return row.conversationId !== null && row.conversationId === this.activePeerId();
  }

  protected readonly talkedQuery = signal('');
  protected readonly notTalkedQuery = signal('');
  protected readonly groupQuery = signal('');

  protected readonly articleRows = this.rowsService.articleRows;
  protected readonly rowErrors = this.rowsService.rowErrors;
  protected readonly pendingGroupIds = this.rowsService.pendingGroupIds;

  protected readonly filteredTalked = computed(() =>
    filterByQuery(this.rowsService.talkedPeople(), this.talkedQuery()),
  );
  protected readonly filteredNotTalked = computed(() =>
    filterByQuery(this.rowsService.notTalkedPeople(), this.notTalkedQuery()),
  );
  protected readonly filteredGroups = computed(() =>
    filterByQuery(this.rowsService.groupRows(), this.groupQuery()),
  );

  ngOnInit(): void {
    this.rowsService.ensureLoaded();
  }

  protected groupRowAriaLabel(row: GroupRow): string {
    if (row.isMember) {
      return 'chat.directory.groupRowAriaLabel';
    }
    return row.visibility === 'REQUEST_TO_JOIN'
      ? 'chat.directory.requestToJoinAriaLabel'
      : 'chat.directory.joinGroupAriaLabel';
  }

  protected onPersonClick(row: PersonRow): void {
    this.rowsService.onPersonClick(row);
  }

  protected onGroupClick(row: GroupRow): void {
    this.rowsService.onGroupClick(row);
  }
}

function filterByQuery<T extends { displayName: string }>(rows: T[], query: string): T[] {
  const q = query.trim().toLowerCase();
  if (q === '') {
    return rows;
  }
  return rows.filter((row) => row.displayName.toLowerCase().includes(q));
}
