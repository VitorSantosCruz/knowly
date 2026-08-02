import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { GlobalPermission } from './global-permission';

interface OwnGlobalPermissionsResponse {
  permissions: GlobalPermission[];
  // Added by the backend's staff-rbac-split REQ-9 (OwnGlobalPermissionsDto): true for any
  // STAFF/STAFF_ADMIN account regardless of granted permissions, false for a plain
  // MEMBER/MEMBER_ADMIN — the signal navigation-menu's canSwitchTenant/canLeaveTenant needed to
  // tell a zero-grant STAFF account apart from a plain MEMBER, which an empty `permissions` list
  // alone can't distinguish.
  isStaffAccount: boolean;
}

@Injectable({ providedIn: 'root' })
export class GlobalPermissionsService {
  private readonly http = inject(HttpClient);

  private readonly _permissions = signal<GlobalPermission[] | null>(null);
  readonly permissions = this._permissions.asReadonly();

  private readonly _isStaffAccount = signal(false);
  readonly isStaffAccount = this._isStaffAccount.asReadonly();

  fetch(): void {
    this.http.get<OwnGlobalPermissionsResponse>('/api/staff/permissions').subscribe((response) => {
      this._permissions.set(response.permissions);
      this._isStaffAccount.set(response.isStaffAccount);
    });
  }

  has(permission: GlobalPermission): boolean {
    return this._permissions()?.includes(permission) ?? false;
  }
}
