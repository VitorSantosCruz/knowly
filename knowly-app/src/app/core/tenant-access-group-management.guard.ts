import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { GlobalPermission } from './global-permission';

interface ActiveTenantResponse {
  tenantId: number;
  tenantName: string;
  role?: 'MEMBER_ADMIN' | 'MEMBER';
}

interface OwnGlobalPermissionsResponse {
  permissions: GlobalPermission[];
}

/**
 * Guards `/tenants/access-groups` (REQ-2). Unlike `accessGroupManagementGuard` (the *global*
 * staff screen's guard, gated purely on one `GlobalPermission`), the backend's
 * `TenantService#requireAdminOfTenantOrStaff` lets a real `MEMBER_ADMIN` of the active tenant
 * through unconditionally on every access-group endpoint this screen calls, regardless of
 * whether they hold `TENANT_ACCESS_GROUP_VIEW` as a `GlobalPermission` — a plain tenant member
 * never has any `GlobalPermission` at all, only the unrelated, narrower tenant `Permission`
 * enum (which does not include `TENANT_ACCESS_GROUP_VIEW`). Gating on `GlobalPermission` alone,
 * as PLAN.md originally sketched via `GET /api/tenants/permissions`, would therefore lock every
 * real MEMBER_ADMIN out of a screen the backend already lets them use — this mirrors
 * `member-detail-panel.component.ts`'s own established `viewerCanManageDirectPermissions`
 * MEMBER_ADMIN-bypass shape instead (see PLAN.md's "Deviations from this PLAN").
 *
 * `GET /api/tenants/active` supplies the active tenant's role (omitted entirely for a staff
 * session with no real `TenantMembership` row); `GET /api/staff/permissions` supplies the
 * `GlobalPermission` set for the staff-caller path. Both calls fail closed to "no access" via
 * `catchError`, so a network error redirects the same way a missing permission does rather than
 * surfacing as a broken Router navigation.
 */
export const tenantAccessGroupManagementGuard: CanActivateFn = () => {
  const http = inject(HttpClient);
  const router = inject(Router);

  const active$ = http
    .get<ActiveTenantResponse | null>('/api/tenants/active')
    .pipe(catchError(() => of(null)));

  const globalPermissions$ = http
    .get<OwnGlobalPermissionsResponse>('/api/staff/permissions')
    .pipe(catchError(() => of({ permissions: [] as GlobalPermission[] })));

  return forkJoin([active$, globalPermissions$]).pipe(
    map(([active, globalPermissions]) => {
      const isMemberAdmin = active?.role === 'MEMBER_ADMIN';
      const hasGlobalPermission = globalPermissions.permissions.includes(
        'TENANT_ACCESS_GROUP_VIEW',
      );

      return isMemberAdmin || hasGlobalPermission ? true : router.parseUrl('/select-tenant');
    }),
  );
};
