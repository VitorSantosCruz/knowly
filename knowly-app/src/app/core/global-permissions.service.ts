import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { GlobalPermission } from './global-permission';

interface OwnGlobalPermissionsResponse {
  permissions: GlobalPermission[];
}

@Injectable({ providedIn: 'root' })
export class GlobalPermissionsService {
  private readonly http = inject(HttpClient);

  private readonly _permissions = signal<GlobalPermission[] | null>(null);
  readonly permissions = this._permissions.asReadonly();

  fetch(): void {
    this.http
      .get<OwnGlobalPermissionsResponse>('/api/staff/permissions')
      .subscribe((response) => this._permissions.set(response.permissions));
  }

  has(permission: GlobalPermission): boolean {
    return this._permissions()?.includes(permission) ?? false;
  }
}
