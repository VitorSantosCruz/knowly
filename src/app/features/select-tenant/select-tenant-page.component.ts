import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of, tap } from 'rxjs';
import {
  ActiveTenantService,
  TenantMembership,
  TenantSummary,
} from '../../core/active-tenant.service';

interface TenantOption {
  tenantId: number;
  tenantName: string;
}

@Component({
  selector: 'app-select-tenant-page',
  imports: [TranslocoPipe, RouterLink],
  template: `
    <div data-testid="select-tenant-page" class="mx-auto max-w-md p-6">
      <h1 class="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
        {{ 'selectTenant.title' | transloco }}
      </h1>
      @if (options().length > 0) {
        <ul class="flex flex-col gap-2">
          @for (option of options(); track option.tenantId) {
            <li>
              <button
                [attr.data-testid]="'select-tenant-' + option.tenantId"
                (click)="onSelect(option)"
                class="w-full rounded-lg border border-slate-200 px-4 py-3 text-left hover:bg-slate-100 dark:border-slate-800 dark:hover:bg-slate-800"
              >
                {{ option.tenantName }}
              </button>
            </li>
          }
        </ul>
      } @else if (loaded()) {
        <p data-testid="select-tenant-empty" class="text-slate-600 dark:text-slate-400">
          {{ 'selectTenant.empty' | transloco }}
        </p>
      }

      @if (isStaff()) {
        <a
          data-testid="create-tenant-link"
          routerLink="/tenants/new"
          class="mt-4 inline-block text-sm text-indigo-600 hover:underline"
        >
          {{ 'selectTenant.createTenant' | transloco }}
        </a>
      }
    </div>
  `,
})
export class SelectTenantPageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  protected readonly options = signal<TenantOption[]>([]);
  protected readonly loaded = signal(false);
  protected readonly isStaff = signal(false);

  ngOnInit(): void {
    this.activeTenantService.list().subscribe((memberships) => {
      if (memberships.length > 0) {
        this.options.set(memberships.map(toOption));
        this.loaded.set(true);
        return;
      }

      this.activeTenantService
        .listAllTenants()
        .pipe(
          tap(() => this.isStaff.set(true)),
          catchError(() => of([] as TenantSummary[])),
        )
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
      .subscribe(() => this.router.navigateByUrl('/dashboard'));
  }
}

function toOption(membership: TenantMembership): TenantOption {
  return { tenantId: membership.tenantId, tenantName: membership.tenantName };
}
