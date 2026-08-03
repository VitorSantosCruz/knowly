import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { Subject, catchError, debounceTime, distinctUntilChanged, of } from 'rxjs';
import { LucidePlus } from '@lucide/angular';
import { buttonClass } from '../../shared/button-classes';
import {
  ActiveTenantService,
  PageResponse,
  TenantMembership,
  TenantSummary,
} from '../../core/active-tenant.service';
import { GlobalPermissionsService } from '../../core/global-permissions.service';

interface TenantOption {
  tenantId: number;
  tenantName: string;
}

const PAGE_SIZE = 20;

@Component({
  selector: 'app-select-tenant-page',
  imports: [TranslocoPipe, RouterLink, LucidePlus],
  template: `
    <div data-testid="select-tenant-page" class="page-shell">
      <div class="enter-fluid mb-6 flex items-center justify-between gap-3">
        <h1 class="font-display text-2xl font-semibold tracking-tight text-ink-900 dark:text-white">
          {{ 'selectTenant.title' | transloco }}
        </h1>
        @if (canCreateTenant()) {
          <a
            data-testid="create-tenant-link"
            routerLink="/tenants/new"
            [class]="createTenantLinkClass"
          >
            <svg lucidePlus class="h-4 w-4" aria-hidden="true"></svg>
            {{ 'selectTenant.createTenant' | transloco }}
          </a>
        }
      </div>
      @if (isFallback()) {
        <div class="enter-fluid mb-4">
          <label for="select-tenant-search" class="sr-only">
            {{ 'selectTenant.searchLabel' | transloco }}
          </label>
          <input
            id="select-tenant-search"
            type="search"
            data-testid="select-tenant-search"
            [placeholder]="'selectTenant.searchPlaceholder' | transloco"
            [attr.aria-label]="'selectTenant.searchLabel' | transloco"
            class="block w-full rounded-xl border border-ink-200/70 bg-white px-4 py-2 text-sm text-ink-900 dark:border-ink-800/70 dark:bg-ink-900 dark:text-white"
            (input)="onSearchInput($event)"
          />
        </div>
      }
      @if (options().length > 0) {
        <ul role="listbox" class="flex w-full flex-col gap-2 border-0">
          @for (option of options(); track option.tenantId) {
            <li role="option" aria-selected="false">
              <button
                type="button"
                [attr.data-testid]="'select-tenant-' + option.tenantId"
                class="block w-full rounded-xl border border-ink-200/70 bg-white px-4 py-3 text-left text-sm text-ink-900 transition-colors duration-fast ease-fluid hover:border-signal-400 dark:border-ink-800/70 dark:bg-ink-900 dark:text-white dark:hover:border-signal-500"
                (click)="onSelect(option)"
              >
                {{ option.tenantName }}
              </button>
            </li>
          }
        </ul>
      } @else if (loaded() && isFallback() && fallbackError() === null) {
        <p
          data-testid="select-tenant-no-results"
          class="enter-fluid text-ink-600 dark:text-ink-400"
        >
          {{ 'selectTenant.noSearchResults' | transloco }}
        </p>
      } @else if (loaded()) {
        <p data-testid="select-tenant-empty" class="enter-fluid text-ink-600 dark:text-ink-400">
          {{ 'selectTenant.empty' | transloco }}
        </p>
      }
      @if (isFallback() && totalPages() > 1) {
        <div class="enter-fluid mt-4 flex items-center justify-between gap-3">
          <button
            type="button"
            data-testid="select-tenant-prev-page"
            [class]="pageButtonClass"
            [disabled]="page() === 0"
            (click)="onPageChange(-1)"
          >
            {{ 'selectTenant.previousPage' | transloco }}
          </button>
          <button
            type="button"
            data-testid="select-tenant-next-page"
            [class]="pageButtonClass"
            [disabled]="page() === totalPages() - 1"
            (click)="onPageChange(1)"
          >
            {{ 'selectTenant.nextPage' | transloco }}
          </button>
        </div>
      }
    </div>
  `,
})
export class SelectTenantPageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly router = inject(Router);

  protected readonly createTenantLinkClass = buttonClass('primary');
  protected readonly pageButtonClass = buttonClass('secondary');
  protected readonly options = signal<TenantOption[]>([]);
  protected readonly loaded = signal(false);
  protected readonly isFallback = signal(false);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly searchTerm = signal('');
  protected readonly fallbackError = signal<'network' | null>(null);
  protected readonly canCreateTenant = computed(() =>
    this.globalPermissionsService.has('TENANT_CREATE'),
  );

  private readonly searchInput$ = new Subject<string>();

  ngOnInit(): void {
    this.globalPermissionsService.fetch();

    this.searchInput$.pipe(debounceTime(300), distinctUntilChanged()).subscribe((term) => {
      this.searchTerm.set(term);
      this.page.set(0);
      this.fetchFallbackTenants();
    });

    this.activeTenantService.list().subscribe((memberships) => {
      if (memberships.length > 0) {
        this.options.set(memberships.map(toOption));
        this.loaded.set(true);
        return;
      }

      this.isFallback.set(true);
      this.fetchFallbackTenants();
    });
  }

  protected onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.searchInput$.next(value);
  }

  protected onPageChange(delta: -1 | 1): void {
    this.page.set(this.page() + delta);
    this.fetchFallbackTenants();
  }

  protected onSelect(option: TenantOption): void {
    this.activeTenantService
      .selectTenant(option.tenantId, option.tenantName)
      .subscribe(() => this.router.navigateByUrl('/welcome'));
  }

  private fetchFallbackTenants(): void {
    this.fallbackError.set(null);
    this.activeTenantService
      .listAllTenants(this.page(), PAGE_SIZE, this.searchTerm() || undefined)
      .pipe(
        catchError(() => {
          this.fallbackError.set('network');
          return of<PageResponse<TenantSummary>>({
            content: [],
            page: 0,
            size: PAGE_SIZE,
            totalElements: 0,
            totalPages: 0,
          });
        }),
      )
      .subscribe((response) => {
        this.options.set(
          response.content.map((tenant) => ({ tenantId: tenant.id, tenantName: tenant.name })),
        );
        this.totalPages.set(response.totalPages);
        this.totalElements.set(response.totalElements);
        this.loaded.set(true);
      });
  }
}

function toOption(membership: TenantMembership): TenantOption {
  return { tenantId: membership.tenantId, tenantName: membership.tenantName };
}
