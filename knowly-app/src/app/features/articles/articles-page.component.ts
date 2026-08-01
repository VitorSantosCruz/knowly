import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, catchError, of } from 'rxjs';
import { buttonClass } from '../../shared/button-classes';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ArticleDetail, ArticleService, ArticleSummary } from '../../core/article.service';
import { PermissionsService } from '../../core/permissions.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

type ArticlesError = 'network' | 'permission-denied' | null;

const POLL_INTERVAL_MS = 4000;

@Component({
  selector: 'app-articles-page',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent, ConfirmDialogComponent],
  template: `
    <div data-testid="articles-page" class="page-shell flex gap-6">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-500 dark:text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <aside class="w-80 shrink-0">
          @if (permissionsService.has('ARTICLE_CREATE')) {
            <div
              data-testid="upload-form-card"
              class="enter-fluid mb-4 rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
            >
              <form
                data-testid="upload-form"
                class="flex flex-col gap-3"
                (submit)="onUpload($event)"
              >
                <input
                  data-testid="upload-title"
                  type="text"
                  placeholder="{{ 'articles.titlePlaceholder' | transloco }}"
                  [value]="uploadTitle()"
                  (input)="uploadTitle.set($any($event.target).value)"
                  [class]="inputClass"
                />
                <label
                  class="flex cursor-pointer items-center justify-between gap-2 rounded-xl border border-dashed border-ink-300 px-3 py-2 text-sm text-ink-500 transition-colors duration-fast ease-fluid hover:border-signal-400 hover:text-signal-600 dark:border-ink-700 dark:text-ink-400 dark:hover:border-signal-500 dark:hover:text-signal-400"
                >
                  <span class="truncate">{{
                    selectedFileName() ?? ('articles.chooseFile' | transloco)
                  }}</span>
                  <input
                    data-testid="upload-file"
                    type="file"
                    (change)="onFileSelected($event)"
                    class="hidden"
                  />
                </label>
                <button type="submit" [class]="uploadButtonClass">
                  {{ 'articles.upload' | transloco }}
                </button>
                @if (uploadError(); as uploadErrorMessage) {
                  <p data-testid="upload-error" class="text-sm text-red-700 dark:text-red-300">
                    {{ uploadErrorMessage }}
                  </p>
                }
              </form>
            </div>
          }

          <ul data-testid="article-list" class="flex flex-col gap-2">
            @for (article of articles(); track article.id) {
              <li
                class="enter-fluid flex items-center justify-between gap-2 rounded-xl border border-ink-200/70 bg-white px-3 py-2.5 shadow-sm transition-colors duration-fast ease-fluid hover:border-ink-300 dark:border-ink-800/70 dark:bg-ink-900 dark:hover:border-ink-700"
              >
                <button
                  [attr.data-testid]="'select-article-' + article.id"
                  (click)="onSelect(article.id)"
                  class="flex min-w-0 flex-1 items-center gap-2 text-left text-sm text-ink-700 transition-colors duration-fast ease-fluid hover:text-signal-600 dark:text-ink-300 dark:hover:text-signal-400"
                >
                  <span class="truncate">{{ article.title }}</span>
                  @switch (article.status) {
                    @case ('PROCESSING') {
                      <span
                        [attr.data-testid]="'article-status-' + article.id"
                        class="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-signal-100 px-2 py-0.5 text-xs font-medium text-signal-700 dark:bg-signal-900/40 dark:text-signal-400"
                      >
                        <svg
                          class="h-3 w-3 animate-spin"
                          viewBox="0 0 24 24"
                          fill="none"
                          aria-hidden="true"
                        >
                          <circle
                            class="opacity-25"
                            cx="12"
                            cy="12"
                            r="10"
                            stroke="currentColor"
                            stroke-width="4"
                          ></circle>
                          <path
                            class="opacity-75"
                            fill="currentColor"
                            d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z"
                          ></path>
                        </svg>
                        {{ 'articles.status.processing' | transloco }}
                      </span>
                    }
                    @case ('FAILED') {
                      <span
                        [attr.data-testid]="'article-status-' + article.id"
                        class="inline-flex shrink-0 items-center rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950/40 dark:text-red-400"
                      >
                        {{ 'articles.status.failed' | transloco }}
                      </span>
                    }
                    @default {
                      <span
                        [attr.data-testid]="'article-status-' + article.id"
                        class="inline-flex shrink-0 items-center rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-400"
                      >
                        {{ 'articles.status.ready' | transloco }}
                      </span>
                    }
                  }
                </button>
                @if (permissionsService.has('ARTICLE_DELETE')) {
                  <button
                    [attr.data-testid]="'delete-article-' + article.id"
                    (click)="onDelete(article.id)"
                    [class]="deleteButtonClass"
                  >
                    {{ 'articles.delete' | transloco }}
                  </button>
                }
              </li>
            }
          </ul>
        </aside>

        @if (pendingDelete(); as articleToDelete) {
          <app-confirm-dialog
            [open]="true"
            [message]="'articles.confirmDelete' | transloco: { title: articleToDelete.title }"
            (confirm)="confirmDelete()"
            (cancel)="cancelDelete()"
          />
        }

        <section class="flex-1">
          @if (selectedDetail(); as detail) {
            <div
              class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-5 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
            >
              <h2 class="font-display mb-2 text-lg font-semibold text-ink-900 dark:text-white">
                {{ detail.title }}
              </h2>
              <p class="mb-4 whitespace-pre-wrap text-sm text-ink-600 dark:text-ink-400">
                {{ detail.text ?? detail.failureReason }}
              </p>
              <a
                data-testid="original-file-link"
                [href]="detail.originalFileUrl"
                target="_blank"
                rel="noopener"
                class="mb-6 inline-block text-sm text-signal-600 transition-colors duration-fast ease-fluid hover:text-signal-500 hover:underline dark:text-signal-400"
              >
                {{ 'articles.originalFile' | transloco }}
              </a>

              @if (permissionsService.has('ARTICLE_EDIT')) {
                <form
                  data-testid="edit-form"
                  class="flex flex-col gap-3"
                  (submit)="onEditSubmit($event)"
                >
                  <input
                    data-testid="edit-title"
                    type="text"
                    [value]="editTitle()"
                    (input)="editTitle.set($any($event.target).value)"
                    [class]="inputClass"
                  />
                  <textarea
                    data-testid="edit-text"
                    [value]="editText()"
                    (input)="editText.set($any($event.target).value)"
                    rows="8"
                    [class]="inputClass"
                  ></textarea>
                  <button type="submit" [class]="uploadButtonClass">
                    {{ 'articles.save' | transloco }}
                  </button>
                </form>
              }
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class ArticlesPageComponent implements OnInit, OnDestroy {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly articleService = inject(ArticleService);
  protected readonly permissionsService = inject(PermissionsService);

  protected readonly articles = signal<ArticleSummary[]>([]);
  protected readonly selectedDetail = signal<ArticleDetail | null>(null);
  protected readonly uploadTitle = signal('');
  protected readonly uploadError = signal<string | null>(null);
  protected readonly editTitle = signal('');
  protected readonly editText = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal<ArticlesError>(null);
  protected readonly selectedFileName = signal<string | null>(null);
  protected readonly pendingDelete = signal<ArticleSummary | null>(null);

  protected readonly inputClass =
    'w-full rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-900 focus:border-signal-500 focus:ring-1 focus:ring-signal-500 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-white';
  protected readonly uploadButtonClass = buttonClass('primary') + ' w-fit';
  protected readonly deleteButtonClass = buttonClass('danger', { ghost: true }) + ' shrink-0';

  private selectedFile: File | null = null;
  private hasLoaded = false;
  private pollTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const tenantId = this.activeTenantService.activeTenantId();

      if (tenantId !== null && !this.hasLoaded) {
        this.hasLoaded = true;
        this.loadArticles(tenantId, { isInitialLoad: true });
      }
    });
  }

  ngOnInit(): void {
    this.activeTenantService.fetch();
    this.permissionsService.fetch();
  }

  ngOnDestroy(): void {
    if (this.pollTimer !== undefined) {
      clearTimeout(this.pollTimer);
    }
  }

  private loadArticles(tenantId: number, { isInitialLoad }: { isInitialLoad: boolean }): void {
    if (isInitialLoad) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.articleService
      .list(tenantId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return of<ArticleSummary[]>([]);
        }),
      )
      .subscribe((articles) => {
        if (isInitialLoad || !this.sameArticles(this.articles(), articles)) {
          this.articles.set(articles);
        }
        if (isInitialLoad) {
          this.loading.set(false);
        }
        this.schedulePollIfNeeded(tenantId, articles);
      });
  }

  private sameArticles(a: ArticleSummary[], b: ArticleSummary[]): boolean {
    if (a.length !== b.length) {
      return false;
    }
    return a.every(
      (article, index) =>
        article.id === b[index].id &&
        article.title === b[index].title &&
        article.status === b[index].status,
    );
  }

  private schedulePollIfNeeded(tenantId: number, articles: ArticleSummary[]): void {
    if (this.pollTimer !== undefined) {
      clearTimeout(this.pollTimer);
      this.pollTimer = undefined;
    }

    if (articles.some((article) => article.status === 'PROCESSING')) {
      this.pollTimer = setTimeout(
        () => this.loadArticles(tenantId, { isInitialLoad: false }),
        POLL_INTERVAL_MS,
      );
    }
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.selectedFileName.set(this.selectedFile?.name ?? null);
  }

  protected onUpload(event: Event): void {
    event.preventDefault();
    const tenantId = this.activeTenantService.activeTenantId();
    const title = this.uploadTitle();
    const file = this.selectedFile;

    if (tenantId === null || !title || !file) {
      return;
    }

    this.uploadError.set(null);

    this.articleService
      .upload(tenantId, title, file)
      .pipe(
        catchError((err) => {
          if (err.status === 403) {
            this.error.set('permission-denied');
          } else {
            this.uploadError.set(
              err.error?.code === 'FILE_TOO_LARGE'
                ? 'This file is too large.'
                : 'This file type is not supported.',
            );
          }
          return EMPTY;
        }),
      )
      .subscribe((article) => {
        this.uploadTitle.set('');
        this.selectedFile = null;
        this.selectedFileName.set(null);
        this.articles.update((articles) => [article, ...articles]);
        this.schedulePollIfNeeded(tenantId, this.articles());
      });
  }

  protected onSelect(articleId: number): void {
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

    this.articleService
      .getDetail(tenantId, articleId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe((detail) => {
        this.selectedDetail.set(detail);
        this.editTitle.set(detail.title);
        this.editText.set(detail.text ?? '');
      });
  }

  protected onEditSubmit(event: Event): void {
    event.preventDefault();
    const tenantId = this.activeTenantService.activeTenantId();
    const detail = this.selectedDetail();

    if (tenantId === null || detail === null) {
      return;
    }

    this.articleService
      .update(tenantId, detail.id, this.editTitle(), this.editText())
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe((updated) => {
        this.selectedDetail.set(updated);
        this.articles.update((articles) =>
          articles.map((article) =>
            article.id === updated.id
              ? { id: updated.id, title: updated.title, status: updated.status }
              : article,
          ),
        );
      });
  }

  protected onDelete(articleId: number): void {
    const article = this.articles().find((a) => a.id === articleId);

    if (article === undefined) {
      return;
    }

    this.pendingDelete.set(article);
  }

  protected confirmDelete(): void {
    const tenantId = this.activeTenantService.activeTenantId();
    const article = this.pendingDelete();

    this.pendingDelete.set(null);

    if (tenantId === null || article === null) {
      return;
    }

    this.performDelete(tenantId, article.id);
  }

  protected cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  private performDelete(tenantId: number, articleId: number): void {
    this.articleService
      .remove(tenantId, articleId)
      .pipe(
        catchError((err) => {
          this.error.set(err.status === 403 ? 'permission-denied' : 'network');
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.articles.update((articles) => articles.filter((article) => article.id !== articleId));

        if (this.selectedDetail()?.id === articleId) {
          this.selectedDetail.set(null);
        }
      });
  }
}
