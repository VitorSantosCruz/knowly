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
}
