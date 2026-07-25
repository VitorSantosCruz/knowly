import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { GlobalPermission } from './global-permission';

interface OwnGlobalPermissionsResponse {
  permissions: GlobalPermission[];
}

/**
 * Blocks routes reserved for staff holding TENANT_CREATE (e.g. tenant creation).
 * Previously inferred "is staff" from GET /api/tenants succeeding — that heuristic
 * broke once the backend's staff-rbac-split feature made staff access individually
 * granted per action: a STAFF user granted only TENANT_CREATE (not TENANT_ACT_AS_ANY,
 * which GET /api/tenants requires) would be wrongly blocked. Checking the actual
 * permission this route needs, via GET /api/staff/permissions (which never 403s —
 * a non-staff caller just gets an empty list), fixes that.
 */
export const staffGuard: CanActivateFn = () => {
  const http = inject(HttpClient);
  const router = inject(Router);

  return http
    .get<OwnGlobalPermissionsResponse>('/api/staff/permissions')
    .pipe(
      map((response) =>
        response.permissions.includes('TENANT_CREATE') ? true : router.parseUrl('/select-tenant'),
      ),
    );
};
