import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { ActiveTenantService } from './active-tenant.service';

/**
 * Blocks tenant-scoped routes until a multi-membership user has picked an active tenant,
 * matching the backend's own TENANT_SELECTION_REQUIRED gate — without this, the page would
 * load with no active tenant and every tenant-scoped API call would fail with a 409.
 */
export const tenantSelectionGuard: CanActivateFn = () => {
  const activeTenantService = inject(ActiveTenantService);
  const router = inject(Router);

  return activeTenantService.list().pipe(
    map((memberships) => {
      const hasActiveMembership = memberships.some((membership) => membership.active);

      return hasActiveMembership || memberships.length <= 1
        ? true
        : router.parseUrl('/select-tenant');
    }),
  );
};
