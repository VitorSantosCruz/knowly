import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { GlobalPermission } from './global-permission';

interface OwnGlobalPermissionsResponse {
  permissions: GlobalPermission[];
}

/**
 * Guards `/staff/access-groups`, mirroring `staffGuard`'s fixed pattern
 * (checked against `GET /api/staff/permissions`, which never 403s — a
 * non-staff caller just gets an empty list). Gated on
 * `STAFF_PERMISSION_MANAGE`, the exact `GlobalPermission` every
 * access-group endpoint in `StaffController`/`StaffService` already
 * requires (`@RequiresGlobalPermission(GlobalPermission.STAFF_PERMISSION_MANAGE)`
 * on list/create/assign/unassign) — this adds no new backend gate, only a
 * frontend route pointing at existing, already-gated calls.
 */
export const accessGroupManagementGuard: CanActivateFn = () => {
  const http = inject(HttpClient);
  const router = inject(Router);

  return http
    .get<OwnGlobalPermissionsResponse>('/api/staff/permissions')
    .pipe(
      map((response) =>
        response.permissions.includes('STAFF_PERMISSION_MANAGE')
          ? true
          : router.parseUrl('/select-tenant'),
      ),
    );
};
