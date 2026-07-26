import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { catchError, of } from 'rxjs';
import { ButtonDirective } from 'primeng/button';
import { Listbox } from 'primeng/listbox';
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
  imports: [TranslocoPipe, RouterLink, ButtonDirective, Listbox],
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
          <a data-testid="create-tenant-link" routerLink="/tenants/new" pButton icon="pi pi-plus">
            {{ 'selectTenant.createTenant' | transloco }}
          </a>
        }
      </div>
      @if (options().length > 0) {
        <p-listbox
          [options]="options()"
          optionLabel="tenantName"
          styleClass="w-full border-0"
          listStyleClass="flex flex-col gap-2"
          (onClick)="onSelect($event.option)"
        >
          <ng-template #item let-option>
            <span [attr.data-testid]="'select-tenant-' + option.tenantId" class="block w-full">
              {{ option.tenantName }}
            </span>
          </ng-template>
        </p-listbox>
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
