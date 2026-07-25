import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { ActiveTenantService } from './active-tenant.service';

/**
 * Blocks tenant-scoped routes until a multi-membership user has picked an active tenant,
 * matching the backend's own TENANT_SELECTION_REQUIRED gate — without this, the page would
 * load with no active tenant and every tenant-scoped API call would fail with a 409.
 *
 * A 0-membership session (staff) is let through unconditionally rather than sent to
 * /select-tenant: staff land on the dashboard directly and use the nav menu's "switch
 * tenant" link when they want to act as a specific tenant (see select-tenant SPEC's REQ-5
 * amendment, 2026-07-25) — only an actual multi-membership "which one?" case still redirects.
 */
export const tenantSelectionGuard: CanActivateFn = () => {
  const activeTenantService = inject(ActiveTenantService);
  const router = inject(Router);

  return activeTenantService.list().pipe(
    map((memberships) => {
      const hasActiveMembership = memberships.some((membership) => membership.active);
      const selectionPending = memberships.length > 1 && !hasActiveMembership;

      return selectionPending ? router.parseUrl('/select-tenant') : true;
    }),
  );
};
