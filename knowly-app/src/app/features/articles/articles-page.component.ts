import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EMPTY, catchError, of } from 'rxjs';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { ArticleDetail, ArticleService, ArticleSummary } from '../../core/article.service';
import { PermissionsService } from '../../core/permissions.service';
import { ErrorStateComponent } from '../../shared/error-state.component';
import { NoAccessStateComponent } from '../../shared/no-access-state.component';

type ArticlesError = 'network' | 'permission-denied' | null;

const POLL_INTERVAL_MS = 4000;

@Component({
  selector: 'app-articles-page',
  imports: [TranslocoPipe, ErrorStateComponent, NoAccessStateComponent],
  template: `
    <div data-testid="articles-page" class="flex gap-6 bg-ink-50 p-6 dark:bg-ink-950">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-ink-500 dark:text-ink-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <aside class="w-80 shrink-0">
          @if (permissionsService.has('ARTICLE_CREATE')) {
            <form
              data-testid="upload-form"
              class="enter-fluid mb-4 flex flex-col gap-3 rounded-2xl border border-ink-200/70 bg-white p-4 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
              (submit)="onUpload($event)"
            >
              <input
                data-testid="upload-title"
                type="text"
                placeholder="{{ 'articles.titlePlaceholder' | transloco }}"
                [value]="uploadTitle()"
                (input)="uploadTitle.set($any($event.target).value)"
                class="rounded-xl border border-ink-300/70 bg-white px-3 py-2 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
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
              <button
                type="submit"
                class="rounded-xl bg-ink-800 px-3 py-2 text-sm font-semibold text-white shadow-sm shadow-ink-900/20 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-signal-600 hover:shadow-md active:translate-y-0 active:scale-[0.98] active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
              >
                {{ 'articles.upload' | transloco }}
              </button>
              @if (uploadError(); as uploadErrorMessage) {
                <p data-testid="upload-error" class="text-sm text-red-700 dark:text-red-300">
                  {{ uploadErrorMessage }}
                </p>
              }
            </form>
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
                    class="shrink-0 rounded-full px-2 py-1 text-sm text-red-600 transition-colors duration-fast ease-fluid hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/40"
                  >
                    {{ 'articles.delete' | transloco }}
                  </button>
                }
              </li>
            }
          </ul>
        </aside>

        <section class="flex-1">
          @if (selectedDetail(); as detail) {
            <div
              class="enter-fluid rounded-2xl border border-ink-200/70 bg-white p-6 shadow-lg shadow-ink-900/5 dark:border-ink-800/70 dark:bg-ink-900 dark:shadow-none"
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
                    class="rounded-xl border border-ink-300/70 bg-white px-3 py-2 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
                  />
                  <textarea
                    data-testid="edit-text"
                    [value]="editText()"
                    (input)="editText.set($any($event.target).value)"
                    rows="8"
                    class="rounded-xl border border-ink-300/70 bg-white px-3 py-2 text-sm text-ink-900 shadow-sm transition-shadow duration-fast ease-fluid focus:border-signal-400 focus:ring-2 focus:ring-signal-400/30 focus:outline-none dark:border-ink-700 dark:bg-ink-800 dark:text-ink-100"
                  ></textarea>
                  <button
                    type="submit"
                    class="w-fit rounded-xl bg-ink-800 px-4 py-2 text-sm font-semibold text-white shadow-sm shadow-ink-900/20 transition-all duration-fast ease-fluid hover:-translate-y-0.5 hover:bg-signal-600 hover:shadow-md active:translate-y-0 active:scale-[0.98] active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
                  >
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

  private selectedFile: File | null = null;
  private hasLoaded = false;
  private pollTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    effect(() => {
      const tenantId = this.activeTenantService.activeTenantId();

      if (tenantId !== null && !this.hasLoaded) {
        this.hasLoaded = true;
        this.loadArticles(tenantId);
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

  private loadArticles(tenantId: number): void {
    this.loading.set(true);
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
        this.articles.set(articles);
        this.loading.set(false);
        this.schedulePollIfNeeded(tenantId, articles);
      });
  }

  private schedulePollIfNeeded(tenantId: number, articles: ArticleSummary[]): void {
    if (this.pollTimer !== undefined) {
      clearTimeout(this.pollTimer);
      this.pollTimer = undefined;
    }

    if (articles.some((article) => article.status === 'PROCESSING')) {
      this.pollTimer = setTimeout(() => this.loadArticles(tenantId), POLL_INTERVAL_MS);
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
    const tenantId = this.activeTenantService.activeTenantId();

    if (tenantId === null) {
      return;
    }

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
