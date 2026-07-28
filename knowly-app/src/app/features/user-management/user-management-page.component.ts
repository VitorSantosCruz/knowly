import { Component, OnInit, inject } from '@angular/core';
import { ActiveTenantService } from '../../core/active-tenant.service';
import { MembersPageComponent } from '../members/members-page.component';
import { StaffDirectoryPageComponent } from './staff-directory-page.component';

/**
 * Same route (`/members`), one wrapper switching on whether the session has an active
 * tenant: MembersPageComponent unchanged when it does, StaffDirectoryPageComponent when it
 * doesn't (staff, no active tenant). Waits for `activeTenantResolved()` before deciding —
 * activeTenantId() is null both while loading and when genuinely staff-with-no-tenant, so
 * branching before the fetch resolves would flash the staff directory before flipping to
 * the tenant view.
 */
@Component({
  selector: 'app-user-management-page',
  imports: [MembersPageComponent, StaffDirectoryPageComponent],
  template: `
    @if (!activeTenantService.activeTenantResolved()) {
      <p data-testid="loading-state" class="page-shell text-sm text-ink-400">…</p>
    } @else if (activeTenantService.activeTenantId() !== null) {
      <app-members-page />
    } @else {
      <app-staff-directory-page />
    }
  `,
})
export class UserManagementPageComponent implements OnInit {
  protected readonly activeTenantService = inject(ActiveTenantService);

  ngOnInit(): void {
    this.activeTenantService.fetch();
  }
}
