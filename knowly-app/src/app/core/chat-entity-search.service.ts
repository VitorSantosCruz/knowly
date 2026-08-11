import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import {
  ChatEntitySearchResponseDto,
  ChatEntitySearchResultDto,
  ChatEntitySearchSectionDto,
  ChatEntitySearchSectionStatus,
  ChatGroupSearchResultDto,
  ChatPersonSearchResultDto,
  ChatRagConversationSearchResultDto,
  ChatRecentPlaceDto,
  ChatSupportSearchResultDto,
} from './chat.model';

type ExpandableSection = 'people' | 'groups' | 'rag';

/**
 * `chat-message-search` PLAN.md, Amended (2026-08-10) — signals-based, owns
 * `GET /api/chat/search` (entity search: people/groups/Support/RAG + "recent places"), kept
 * separate from `ChatMessageSearchService` (still owns `GET /api/chat/messages/search`) mirroring
 * the backend's own service split. See that PLAN's "Partial failure" decision: a transport-level
 * failure on this one HTTP call marks all four entity sections `'error'` simultaneously — "two
 * failure domains, not five," not a true per-entity-kind partial failure.
 */
@Injectable({ providedIn: 'root' })
export class ChatEntitySearchService {
  private readonly http = inject(HttpClient);

  private readonly _people = signal<ChatPersonSearchResultDto[]>([]);
  readonly people = this._people.asReadonly();
  private readonly _peopleHasMore = signal(false);
  readonly peopleHasMore = this._peopleHasMore.asReadonly();
  private readonly _peopleStatus = signal<ChatEntitySearchSectionStatus>('idle');
  readonly peopleStatus = this._peopleStatus.asReadonly();

  private readonly _groups = signal<ChatGroupSearchResultDto[]>([]);
  readonly groups = this._groups.asReadonly();
  private readonly _groupsHasMore = signal(false);
  readonly groupsHasMore = this._groupsHasMore.asReadonly();
  private readonly _groupsStatus = signal<ChatEntitySearchSectionStatus>('idle');
  readonly groupsStatus = this._groupsStatus.asReadonly();

  private readonly _support = signal<ChatSupportSearchResultDto | null>(null);
  readonly support = this._support.asReadonly();
  private readonly _supportStatus = signal<ChatEntitySearchSectionStatus>('idle');
  readonly supportStatus = this._supportStatus.asReadonly();

  private readonly _rag = signal<ChatRagConversationSearchResultDto[]>([]);
  readonly rag = this._rag.asReadonly();
  private readonly _ragHasMore = signal(false);
  readonly ragHasMore = this._ragHasMore.asReadonly();
  private readonly _ragStatus = signal<ChatEntitySearchSectionStatus>('idle');
  readonly ragStatus = this._ragStatus.asReadonly();

  private readonly _recentPlaces = signal<ChatRecentPlaceDto[]>([]);
  /** Named with a trailing underscore to avoid clashing with the `recentPlaces()` fetch method. */
  readonly recentPlaces_ = this._recentPlaces.asReadonly();
  private readonly _recentPlacesStatus = signal<ChatEntitySearchSectionStatus>('idle');
  readonly recentPlacesStatus = this._recentPlacesStatus.asReadonly();

  readonly anyEntityLoading = computed(
    () =>
      this._peopleStatus() === 'loading' ||
      this._groupsStatus() === 'loading' ||
      this._supportStatus() === 'loading' ||
      this._ragStatus() === 'loading',
  );

  /** Bug fix (found live: type-ahead flicker while typing) — see
   * `ChatMessageSearchService#searchGeneration`'s Javadoc for the full race-condition writeup;
   * the same "no cancellation/ordering guarantee on a plain `.subscribe()` per keystroke" bug
   * applies here too, so this service gets the identical generation-token guard. */
  private searchGeneration = 0;

  search(q: string): void {
    this._peopleStatus.set('loading');
    this._groupsStatus.set('loading');
    this._supportStatus.set('loading');
    this._ragStatus.set('loading');

    const generation = ++this.searchGeneration;

    this.http
      .get<ChatEntitySearchResponseDto>('/api/chat/search', {
        params: new HttpParams().set('q', q),
      })
      .pipe(catchError(() => of(null)))
      .subscribe((response) => {
        if (generation !== this.searchGeneration) {
          // A newer search() call has already superseded this one.
          return;
        }
        if (response === null) {
          this._peopleStatus.set('error');
          this._groupsStatus.set('error');
          this._supportStatus.set('error');
          this._ragStatus.set('error');
          return;
        }
        this._people.set(response.people.results);
        this._peopleHasMore.set(response.people.hasMore);
        this._peopleStatus.set('ok');

        this._groups.set(response.groups.results);
        this._groupsHasMore.set(response.groups.hasMore);
        this._groupsStatus.set('ok');

        this._support.set(response.support);
        this._supportStatus.set('ok');

        this._rag.set(response.rag.results);
        this._ragHasMore.set(response.rag.hasMore);
        this._ragStatus.set('ok');
      });
  }

  recentPlaces(): void {
    this._recentPlacesStatus.set('loading');
    this.http
      .get<ChatEntitySearchResultDto>('/api/chat/search')
      .pipe(catchError(() => of(null)))
      .subscribe((response) => {
        if (response === null) {
          this._recentPlacesStatus.set('error');
          return;
        }
        this._recentPlaces.set(response.recentPlaces);
        this._recentPlacesStatus.set('ok');
      });
  }

  expandSection(type: ExpandableSection, q: string): void {
    const offset = this.sectionResults(type).length;
    const params = new HttpParams().set('q', q).set('type', type).set('offset', offset);

    this.http
      .get<ChatEntitySearchSectionDto<unknown>>('/api/chat/search', { params })
      .pipe(catchError(() => of(null)))
      .subscribe((section) => {
        if (section === null) {
          this.sectionStatusSignal(type).set('error');
          return;
        }
        this.appendToSection(type, section);
      });
  }

  reset(): void {
    this._people.set([]);
    this._peopleHasMore.set(false);
    this._peopleStatus.set('idle');

    this._groups.set([]);
    this._groupsHasMore.set(false);
    this._groupsStatus.set('idle');

    this._support.set(null);
    this._supportStatus.set('idle');

    this._rag.set([]);
    this._ragHasMore.set(false);
    this._ragStatus.set('idle');

    this._recentPlaces.set([]);
    this._recentPlacesStatus.set('idle');

    // Also supersedes any in-flight search() response — reopening the dropdown always starts
    // clean, never gets repopulated by a request that was already in flight when it closed.
    this.searchGeneration += 1;
  }

  private sectionResults(type: ExpandableSection): unknown[] {
    if (type === 'people') return this._people();
    if (type === 'groups') return this._groups();
    return this._rag();
  }

  private sectionStatusSignal(type: ExpandableSection) {
    if (type === 'people') return this._peopleStatus;
    if (type === 'groups') return this._groupsStatus;
    return this._ragStatus;
  }

  private appendToSection(
    type: ExpandableSection,
    section: ChatEntitySearchSectionDto<unknown>,
  ): void {
    if (type === 'people') {
      this._people.update((existing) => [
        ...existing,
        ...(section.results as ChatPersonSearchResultDto[]),
      ]);
      this._peopleHasMore.set(section.hasMore);
      this._peopleStatus.set('ok');
      return;
    }
    if (type === 'groups') {
      this._groups.update((existing) => [
        ...existing,
        ...(section.results as ChatGroupSearchResultDto[]),
      ]);
      this._groupsHasMore.set(section.hasMore);
      this._groupsStatus.set('ok');
      return;
    }
    this._rag.update((existing) => [
      ...existing,
      ...(section.results as ChatRagConversationSearchResultDto[]),
    ]);
    this._ragHasMore.set(section.hasMore);
    this._ragStatus.set('ok');
  }
}
