import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import {
  ActiveTenantService,
  TenantMembership,
  TenantSummary,
} from '../../core/active-tenant.service';
import { GlobalPermissionsService } from '../../core/global-permissions.service';

interface TenantOption {
  tenantId: number;
  tenantName: string;
}

@Component({
  selector: 'app-select-tenant-page',
  imports: [TranslocoPipe, RouterLink],
  template: `
    <div
      data-testid="select-tenant-page"
      class="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6"
    >
      <div class="enter-fluid mb-6 flex items-center justify-between gap-3">
        <h1 class="font-display text-2xl font-semibold tracking-tight text-ink-900 dark:text-white">
          {{ 'selectTenant.title' | transloco }}
        </h1>
        @if (canCreateTenant()) {
          <a
            data-testid="create-tenant-link"
            routerLink="/tenants/new"
            class="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-ink-800 px-3 py-2 text-sm font-medium text-white shadow-sm shadow-ink-900/20 transition-colors duration-fast ease-fluid hover:bg-signal-600 active:bg-signal-700 dark:bg-ink-600 dark:hover:bg-signal-500"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="h-4 w-4"
              aria-hidden="true"
            >
              <path d="M5 12h14" />
              <path d="M12 5v14" />
            </svg>
            {{ 'selectTenant.createTenant' | transloco }}
          </a>
        }
      </div>
      @if (options().length > 0) {
        <ul class="flex flex-col gap-2">
          @for (option of options(); track option.tenantId) {
            <li class="enter-fluid">
              <button
                [attr.data-testid]="'select-tenant-' + option.tenantId"
                (click)="onSelect(option)"
                class="w-full rounded-xl border border-ink-200/70 bg-white px-4 py-3 text-left text-ink-800 shadow-sm shadow-ink-900/5 transition-colors duration-fast ease-fluid hover:border-signal-300 hover:bg-signal-50 dark:border-ink-800/70 dark:bg-ink-900 dark:text-ink-100 dark:shadow-none dark:hover:border-signal-700 dark:hover:bg-ink-800"
              >
                {{ option.tenantName }}
              </button>
            </li>
          }
        </ul>
      } @else if (loaded()) {
        <p data-testid="select-tenant-empty" class="enter-fluid text-ink-600 dark:text-ink-400">
          {{ 'selectTenant.empty' | transloco }}
        </p>
      }
    </div>
  `,
})
export class SelectTenantPageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly globalPermissionsService = inject(GlobalPermissionsService);
  private readonly router = inject(Router);

  protected readonly options = signal<TenantOption[]>([]);
  protected readonly loaded = signal(false);
  protected readonly canCreateTenant = computed(() =>
    this.globalPermissionsService.has('TENANT_CREATE'),
  );

  ngOnInit(): void {
    this.globalPermissionsService.fetch();

    this.activeTenantService.list().subscribe((memberships) => {
      if (memberships.length > 0) {
        this.options.set(memberships.map(toOption));
        this.loaded.set(true);
        return;
      }

      this.activeTenantService
        .listAllTenants()
        .pipe(catchError(() => of([] as TenantSummary[])))
        .subscribe((tenants) => {
          this.options.set(
            tenants.map((tenant) => ({ tenantId: tenant.id, tenantName: tenant.name })),
          );
          this.loaded.set(true);
        });
    });
  }

  protected onSelect(option: TenantOption): void {
    this.activeTenantService
      .selectTenant(option.tenantId, option.tenantName)
      .subscribe(() => this.router.navigateByUrl('/welcome'));
  }
}

function toOption(membership: TenantMembership): TenantOption {
  return { tenantId: membership.tenantId, tenantName: membership.tenantName };
}
