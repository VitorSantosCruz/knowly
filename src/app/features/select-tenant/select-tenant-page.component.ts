import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ActiveTenantService, TenantMembership } from '../../core/active-tenant.service';

@Component({
  selector: 'app-select-tenant-page',
  imports: [TranslocoPipe],
  template: `
    <div data-testid="select-tenant-page" class="mx-auto max-w-md p-6">
      <h1 class="mb-4 text-lg font-semibold text-slate-900 dark:text-white">
        {{ 'selectTenant.title' | transloco }}
      </h1>
      <ul class="flex flex-col gap-2">
        @for (membership of memberships(); track membership.tenantId) {
          <li>
            <button
              [attr.data-testid]="'select-tenant-' + membership.tenantId"
              (click)="onSelect(membership)"
              class="w-full rounded-lg border border-slate-200 px-4 py-3 text-left hover:bg-slate-100 dark:border-slate-800 dark:hover:bg-slate-800"
            >
              {{ membership.tenantName }}
            </button>
          </li>
        }
      </ul>
    </div>
  `,
})
export class SelectTenantPageComponent implements OnInit {
  private readonly activeTenantService = inject(ActiveTenantService);
  private readonly router = inject(Router);

  protected readonly memberships = signal<TenantMembership[]>([]);

  ngOnInit(): void {
    this.activeTenantService.list().subscribe((memberships) => this.memberships.set(memberships));
  }

  protected onSelect(membership: TenantMembership): void {
    this.activeTenantService
      .selectTenant(membership.tenantId, membership.tenantName)
      .subscribe(() => this.router.navigateByUrl('/dashboard'));
  }
}
