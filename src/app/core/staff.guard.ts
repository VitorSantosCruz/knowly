import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { ActiveTenantService } from './active-tenant.service';

/**
 * Blocks routes reserved for staff (e.g. tenant creation). There is no explicit
 * "isStaff" flag anywhere in the API — GET /api/tenants only succeeds for staff
 * (tenancy REQ-21), so this reuses that same signal instead of adding a new one.
 */
export const staffGuard: CanActivateFn = () => {
  const activeTenantService = inject(ActiveTenantService);
  const router = inject(Router);

  return activeTenantService.listAllTenants().pipe(
    map(() => true),
    catchError(() => of(router.parseUrl('/select-tenant'))),
  );
};
