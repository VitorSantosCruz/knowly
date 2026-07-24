import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Permission } from './permission';

interface OwnPermissionsResponse {
  permissions: Permission[];
}

@Injectable({ providedIn: 'root' })
export class PermissionsService {
  private readonly http = inject(HttpClient);

  private readonly _permissions = signal<Permission[] | null>(null);
  readonly permissions = this._permissions.asReadonly();

  fetch(): void {
    this.http
      .get<OwnPermissionsResponse>('/api/tenants/permissions')
      .subscribe((response) => this._permissions.set(response.permissions));
  }

  has(permission: Permission): boolean {
    return this._permissions()?.includes(permission) ?? false;
  }
}
