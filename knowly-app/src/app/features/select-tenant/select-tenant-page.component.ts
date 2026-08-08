import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  of,
} from 'rxjs';
import { LucidePlus, LucideTrash } from '@lucide/angular';
import { buttonClass } from '../../shared/button-classes';
import {
  ActiveTenantService,
  PageResponse,
  TenantMembership,
  TenantSummary,
} from '../../core/active-tenant.service';
import { GlobalPermissionsService } from '../../core/global-permissions.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { SharedListComponent } from '../../shared/shared-list/shared-list.component';
import { SharedListColumn, SharedListRowAction } from '../../shared/shared-list/shared-list.model';

interface TenantOption {
  tenantId: number;
  tenantName: string;
}

const PAGE_SIZE = 20;

@Component({
  selector: 'app-select-tenant-page',
  imports: [TranslocoPipe, RouterLink, LucidePlus, ConfirmDialogComponent, SharedListComponent],
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
      <app-shared-list
        [title]="'selectTenant.title' | transloco"
        [rows]="options()"
        [columns]="columns"
        [rowId]="rowIdFn"
        [rowActions]="rowActions()"
        [searchable]="isFallback()"
        [searchPlaceholder]="'selectTenant.searchPlaceholder' | transloco"
        [serverPagination]="serverPagination()"
        emptyMessageKey="selectTenant.empty"
        (searchChange)="onSearchInput($event)"
        (pageChange)="onPageChange($event)"
        (rowClick)="onSelect($event)"
      />
    </div>
    @if (pendingDelete(); as tenant) {
      <app-confirm-dialog
        [open]="true"
        [message]="'selectTenant.confirmDelete' | transloco: { name: tenant.tenantName }"
        [fetchToken]="deletionTokenFetcher(tenant.tenantId)"
        [retryToken]="deleteRetryToken()"
        (confirm)="confirmDelete($event)"
        (dismissed)="cancelDelete()"
      />
    }
  `,
})
export class SelectTenantPageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly router = inject(Router);

  protected readonly createTenantLinkClass = buttonClass('primary');
  protected readonly rowIdFn = (option: TenantOption): number => option.tenantId;
  protected readonly columns: SharedListColumn<TenantOption>[] = [
    {
      key: 'tenantName',
      headerKey: 'selectTenant.title',
      render: (option) => ({ type: 'text', value: option.tenantName }),
    },
  ];
  protected readonly serverPagination = computed(() =>
    this.isFallback()
      ? {
          page: this.page(),
          pageSize: PAGE_SIZE,
          totalPages: this.totalPages(),
          totalElements: this.totalElements(),
        }
      : null,
  );
  protected readonly rowActions = computed<SharedListRowAction<TenantOption>[]>(() =>
    this.canDeleteTenant()
      ? [
          {
            icon: LucideTrash,
            labelKey: 'selectTenant.delete',
            variant: 'danger',
            onClick: (option: TenantOption) => this.onDelete(option),
          },
        ]
      : [],
  );
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
  // Backend gates deletion on the global TENANT_DELETE permission
  // (@RequiresGlobalPermission), staff-only -- a regular member never reaches the fallback
  // (all-tenants) listing this button lives in anyway, since they always have memberships.
  protected readonly canDeleteTenant = computed(() =>
    this.globalPermissionsService.has('TENANT_DELETE'),
  );
  protected readonly pendingDelete = signal<TenantOption | null>(null);
  protected readonly deleteRetryToken = signal(0);

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

  protected onSearchInput(value: string): void {
    this.searchInput$.next(value);
  }

  protected onPageChange(delta: number): void {
    this.page.set(this.page() + delta);
    this.fetchFallbackTenants();
  }

  protected onSelect(option: TenantOption): void {
    this.activeTenantService
      .selectTenant(option.tenantId, option.tenantName)
      .subscribe(() => this.router.navigateByUrl('/welcome'));
  }

  protected onDelete(option: TenantOption): void {
    this.pendingDelete.set(option);
  }

  protected deletionTokenFetcher(tenantId: number): () => Observable<string> {
    return () => this.activeTenantService.generateDeletionConfirmationToken(tenantId);
  }

  protected confirmDelete(word: string): void {
    const tenant = this.pendingDelete();

    if (tenant === null) {
      return;
    }

    this.activeTenantService
      .deleteTenant(tenant.tenantId, word)
      .pipe(
        catchError((err) => {
          if (err.status === 400) {
            this.deleteRetryToken.update((n) => n + 1);
          } else {
            this.pendingDelete.set(null);
            this.deleteRetryToken.set(0);
          }
          return EMPTY;
        }),
      )
      .subscribe(() => {
        this.pendingDelete.set(null);
        this.deleteRetryToken.set(0);
        this.options.update((current) => current.filter((o) => o.tenantId !== tenant.tenantId));
      });
  }

  protected cancelDelete(): void {
    this.pendingDelete.set(null);
    this.deleteRetryToken.set(0);
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
