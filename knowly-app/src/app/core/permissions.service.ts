import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { Permission } from './permission';

interface OwnPermissionsResponse {
  permissions: Permission[];
}

@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly http = inject(HttpClient);

  private readonly _permissions = signal<Permission[] | null>(null);
  readonly permissions = this._permissions.asReadonly();

  // Keyed by Permission so multiple "any tenant" checks could coexist, though only
  // PROFILE_EDIT is fetched today (see nav-menu.component.ts).
  private readonly _anyTenantGrants = signal<Partial<Record<Permission, boolean>>>({});

  /**
   * Safe to call with no active tenant (e.g. a staff session that hasn't switched into one
   * yet) — the backend 403s in that case, caught here and left as "no permissions" rather
   * than an unhandled error, so callers never need to know whether a tenant is active first.
   */
  fetch(): void {
    this.http
      .get<OwnPermissionsResponse>('/api/tenants/permissions')
      .pipe(catchError(() => of({ permissions: [] as Permission[] })))
      .subscribe((response) => this._permissions.set(response.permissions));
  }

  has(permission: Permission): boolean {
    return this._permissions()?.includes(permission) ?? false;
  }

  /**
   * REQ-19: "does the caller hold this permission in ANY tenant membership, not just the
   * active one" — evaluated server-side across every TenantMembership. Safe to call with no
   * session-active tenant (same 403-as-zero-grant posture as fetch() above).
   */
  fetchInAnyTenant(permission: Permission): void {
    this.http
      .get<{ granted: boolean }>(`/api/tenants/permissions/any-tenant?permission=${permission}`)
      .pipe(catchError(() => of({ granted: false })))
      .subscribe((response) =>
        this._anyTenantGrants.update((current) => ({ ...current, [permission]: response.granted })),
      );
  }

  hasInAnyTenant(permission: Permission): boolean {
    return this._anyTenantGrants()[permission] ?? false;
  }
}
