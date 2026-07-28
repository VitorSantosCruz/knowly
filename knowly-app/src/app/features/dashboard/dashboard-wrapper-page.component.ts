import { Component, OnInit, inject } from '@angular/core';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { DashboardPageComponent } from './dashboard-page.component';
import { GlobalDashboardPageComponent } from './global-dashboard-page.component';

/**
 * Same route (`/dashboard`), one wrapper switching on whether the session has an active
 * tenant: DashboardPageComponent unchanged when it does, GlobalDashboardPageComponent when
 * it doesn't (staff, no active tenant) — same shape as UserManagementPageComponent. Waits
 * for activeTenantResolved() before deciding, since activeTenantId() is null both while
 * loading and when genuinely staff-with-no-tenant.
 */
@Component({
  selector: 'app-dashboard-wrapper-page',
  imports: [DashboardPageComponent, GlobalDashboardPageComponent],
  template: `
    @if (!activeTenantService.activeTenantResolved()) {
      <p data-testid="loading-state" class="page-shell text-sm text-ink-400">…</p>
    } @else if (activeTenantService.activeTenantId() !== null) {
      <app-dashboard-page />
    } @else {
      <app-global-dashboard-page />
    }
  `,
})
export class DashboardWrapperPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);

  ngOnInit(): void {
    this.activeTenantService.fetch();
  }
}
