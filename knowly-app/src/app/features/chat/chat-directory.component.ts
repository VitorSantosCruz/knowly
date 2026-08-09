import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChatDirectoryService } from '../../core/chat-directory.service';
import { ChatGroupService } from '../../core/chat-group.service';
import { ChatService } from '../../core/chat.service';
import { ProfileService } from '../../core/profile.service';
import { GroupVisibilityBadgeComponent } from './group-visibility-badge.component';
import { CreateGroupDialogComponent } from './create-group-dialog.component';

interface PersonRow {
  kind: 'person';
  key: string;
  userId: number;
  displayName: string;
  conversationId: number | null;
}

interface GroupRow {
  kind: 'group';
  key: string;
  id: number;
  displayName: string;
  visibility: 'PRIVATE' | 'REQUEST_TO_JOIN' | 'PUBLIC' | undefined;
  isMember: boolean;
}

type DirectoryRow = PersonRow | GroupRow;

/**
 * People + Groups combined list (REQ-3, REQ-4, REQ-8, REQ-9, REQ-10, REQ-11) — one shared
 * component, not two near-duplicates, since both are filtered by the identical name-match
 * predicate (PLAN.md). Support/"Base de artigos" are structurally excluded: they're separate
 * sidebar sections, never rows this component could render.
 */
@Component({
  selector: 'app-chat-directory',
  imports: [TranslocoPipe, GroupVisibilityBadgeComponent, CreateGroupDialogComponent],
  template: `
    <div data-testid="chat-directory" class="flex flex-col gap-4">
      <label class="flex flex-col gap-1 text-sm">
        <span class="sr-only">{{ 'chat.directory.searchLabel' | transloco }}</span>
        <input
          type="search"
          data-testid="chat-directory-search"
          [attr.aria-label]="'chat.directory.searchLabel' | transloco"
          [value]="searchQuery()"
          (input)="searchQuery.set($any($event.target).value)"
          placeholder="{{ 'chat.directory.searchPlaceholder' | transloco }}"
          class="rounded-lg border border-ink-200/70 px-3 py-2 text-sm dark:border-ink-800/70"
        />
      </label>

      <div class="flex items-center justify-end">
        <button
          type="button"
          data-testid="chat-directory-create-group"
          [attr.aria-label]="'chat.directory.createGroupAriaLabel' | transloco"
          (click)="createGroupOpen.set(true)"
          class="rounded-lg bg-signal-600 px-2 py-1 text-xs font-medium text-white hover:bg-signal-700"
        >
          {{ 'chat.directory.createGroup' | transloco }}
        </button>
      </div>

      @if (rows().length === 0 && searchQuery() === '') {
        <p data-testid="chat-directory-empty" class="text-sm text-ink-500 dark:text-ink-400">
          {{ 'chat.directory.emptyState' | transloco }}
        </p>
      } @else if (filteredRows().length === 0) {
        <p data-testid="chat-directory-no-results" class="text-sm text-ink-500 dark:text-ink-400">
          {{ 'chat.directory.noResults' | transloco: { query: searchQuery() } }}
        </p>
      } @else {
        @if (peopleRows().length > 0) {
          <div>
            <h2 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
              {{ 'chat.directory.peopleTitle' | transloco }}
            </h2>
            <ul class="flex flex-col gap-1">
              @for (row of peopleRows(); track row.key) {
                <li>
                  <button
                    type="button"
                    [attr.data-testid]="'chat-directory-row-' + row.key"
                    [attr.aria-label]="
                      'chat.directory.personRowAriaLabel' | transloco: { nickname: row.displayName }
                    "
                    (click)="onPersonClick(row)"
                    class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  >
                    {{ row.displayName }}
                  </button>
                  @if (rowErrors().has(row.key)) {
                    <p role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">
                      {{ 'chat.directory.actionError' | transloco }}
                    </p>
                  }
                </li>
              }
            </ul>
          </div>
        }

        @if (groupRows().length > 0) {
          <div>
            <h2 class="mb-2 text-xs font-semibold tracking-wider text-ink-500 uppercase">
              {{ 'chat.directory.groupsTitle' | transloco }}
            </h2>
            <ul class="flex flex-col gap-1">
              @for (row of groupRows(); track row.key) {
                <li>
                  <button
                    type="button"
                    [attr.data-testid]="'chat-directory-row-' + row.key"
                    [attr.aria-label]="
                      groupRowAriaLabel(row) | transloco: { title: row.displayName }
                    "
                    [disabled]="pendingGroupIds().has(row.id)"
                    (click)="onGroupClick(row)"
                    class="flex w-full items-center justify-between rounded-lg border border-ink-200/70 px-3 py-2 text-left text-sm hover:bg-ink-50 disabled:opacity-60 dark:border-ink-800/70 dark:hover:bg-ink-800"
                  >
                    <span>{{ row.displayName }}</span>
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
          </div>
        }
      }
    </div>

    <app-create-group-dialog [open]="createGroupOpen()" (dismissed)="createGroupOpen.set(false)" />
  `,
})
export class ChatDirectoryComponent implements OnInit {
  protected readonly chatService = inject(ChatService);
  private readonly chatDirectoryService = inject(ChatDirectoryService);
  private readonly chatGroupService = inject(ChatGroupService);
  private readonly profileService = inject(ProfileService);
  private readonly router = inject(Router);

  protected readonly searchQuery = signal('');
  protected readonly createGroupOpen = signal(false);
  protected readonly rowErrors = signal<Set<string>>(new Set());
  protected readonly pendingGroupIds = signal<Set<number>>(new Set());

  private readonly currentUserId = signal<number | null>(null);

  private readonly ownDirectConversations = computed(() =>
    this.chatService.conversations().filter((c) => c.kind === 'PEER_DIRECT'),
  );

  private readonly ownGroupConversations = computed(() =>
    this.chatService.conversations().filter((c) => c.kind === 'PEER_GROUP'),
  );

  protected readonly rows = computed<DirectoryRow[]>(() => {
    const people: PersonRow[] = this.chatService.eligibleParticipants().map((candidate) => {
      const existing = this.ownDirectConversations().find((c) =>
        c.participantUserIds.includes(candidate.userId),
      );
      return {
        kind: 'person',
        key: `person:${candidate.userId}`,
        userId: candidate.userId,
        displayName: candidate.nickname,
        conversationId: existing?.id ?? null,
      };
    });

    const ownGroups: GroupRow[] = this.ownGroupConversations().map((c) => ({
      kind: 'group',
      key: `group:${c.id}`,
      id: c.id,
      displayName: c.title ?? '',
      visibility: this.chatService.details().get(c.id)?.visibility,
      isMember: true,
    }));

    // REQ-19/28: the backend never returns a PRIVATE or already-joined group here — no
    // client-side re-filtering of that invariant (see ChatDirectoryService's own doc comment).
    const discoverableGroups: GroupRow[] = this.chatDirectoryService
      .discoverableGroups()
      .map((g) => ({
        kind: 'group',
        key: `group:${g.id}`,
        id: g.id,
        displayName: g.title,
        visibility: g.visibility,
        isMember: false,
      }));

    return [...people, ...ownGroups, ...discoverableGroups];
  });

  protected readonly filteredRows = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    if (query === '') {
      return this.rows();
    }
    return this.rows().filter((row) => row.displayName.toLowerCase().includes(query));
  });

  protected readonly peopleRows = computed(() =>
    this.filteredRows().filter((row): row is PersonRow => row.kind === 'person'),
  );
  protected readonly groupRows = computed(() =>
    this.filteredRows().filter((row): row is GroupRow => row.kind === 'group'),
  );

  ngOnInit(): void {
    this.chatService.fetchConversations();
    this.chatService.fetchEligibleParticipants('direct');
    this.chatDirectoryService.fetchDiscoverableGroups();
    this.profileService
      .getOwnProfile()
      .subscribe((profile) => this.currentUserId.set(profile.userId));
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
    this.clearRowError(row.key);
    if (row.conversationId !== null) {
      this.router.navigate(['/chat', row.conversationId]);
      return;
    }
    this.chatService
      .createConversation({ kind: 'DIRECT', participantUserIds: [row.userId] })
      .subscribe({
        next: (conversation) => this.router.navigate(['/chat', conversation.id]),
        error: () => this.setRowError(row.key),
      });
  }

  protected onGroupClick(row: GroupRow): void {
    this.clearRowError(row.key);
    if (row.isMember) {
      this.router.navigate(['/chat', row.id]);
      return;
    }

    if (row.visibility === 'PUBLIC') {
      this.chatGroupService.join(row.id).subscribe({
        next: () => this.router.navigate(['/chat', row.id]),
        error: () => this.setRowError(row.key),
      });
      return;
    }

    if (row.visibility === 'REQUEST_TO_JOIN') {
      this.chatGroupService.requestToJoin(row.id).subscribe({
        next: () => this.pendingGroupIds.update((ids) => new Set(ids).add(row.id)),
        error: () => this.setRowError(row.key),
      });
    }
  }

  private setRowError(key: string): void {
    this.rowErrors.update((errors) => new Set(errors).add(key));
  }

  private clearRowError(key: string): void {
    this.rowErrors.update((errors) => {
      if (!errors.has(key)) {
        return errors;
      }
      const next = new Set(errors);
      next.delete(key);
      return next;
    });
  }
}
