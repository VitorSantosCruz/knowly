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
    <div data-testid="articles-page" class="flex gap-6 p-6">
      @if (loading()) {
        <p data-testid="loading-state" class="text-sm text-slate-400">…</p>
      } @else if (error() === 'permission-denied') {
        <app-no-access-state />
      } @else if (error() === 'network') {
        <app-error-state />
      } @else {
        <aside class="w-80 shrink-0">
          @if (permissionsService.has('ARTICLE_CREATE')) {
            <form
              data-testid="upload-form"
              class="mb-4 flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900"
              (submit)="onUpload($event)"
            >
              <input
                data-testid="upload-title"
                type="text"
                placeholder="{{ 'articles.titlePlaceholder' | transloco }}"
                [value]="uploadTitle()"
                (input)="uploadTitle.set($any($event.target).value)"
                class="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
              />
              <label
                class="flex cursor-pointer items-center justify-between gap-2 rounded-xl border border-dashed border-slate-300 px-3 py-2 text-sm text-slate-500 transition hover:border-indigo-400 hover:text-indigo-600 dark:border-slate-700 dark:text-slate-400 dark:hover:border-indigo-500 dark:hover:text-indigo-400"
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
                class="rounded-xl bg-indigo-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-500"
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
                class="flex items-center justify-between gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2.5 shadow-sm transition hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700"
              >
                <button
                  [attr.data-testid]="'select-article-' + article.id"
                  (click)="onSelect(article.id)"
                  class="truncate text-left text-sm text-slate-700 transition hover:text-indigo-600 dark:text-slate-300 dark:hover:text-indigo-400"
                >
                  {{ article.title }}
                  <span class="text-slate-400 dark:text-slate-500">— {{ article.status }}</span>
                </button>
                @if (permissionsService.has('ARTICLE_DELETE')) {
                  <button
                    [attr.data-testid]="'delete-article-' + article.id"
                    (click)="onDelete(article.id)"
                    class="shrink-0 rounded-full px-2 py-1 text-sm text-red-600 transition hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-950/40"
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
              class="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900"
            >
              <h2 class="mb-2 text-lg font-semibold text-slate-900 dark:text-white">
                {{ detail.title }}
              </h2>
              <p class="mb-4 whitespace-pre-wrap text-sm text-slate-600 dark:text-slate-400">
                {{ detail.text ?? detail.failureReason }}
              </p>
              <a
                data-testid="original-file-link"
                [href]="detail.originalFileUrl"
                target="_blank"
                rel="noopener"
                class="mb-6 inline-block text-sm text-indigo-600 transition hover:text-indigo-500 hover:underline dark:text-indigo-400"
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
                    class="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
                  />
                  <textarea
                    data-testid="edit-text"
                    [value]="editText()"
                    (input)="editText.set($any($event.target).value)"
                    rows="8"
                    class="rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm transition focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
                  ></textarea>
                  <button
                    type="submit"
                    class="w-fit rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-indigo-500"
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
