import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { GlobalPermission } from './global-permission';

export type GlobalRole = 'STAFF' | 'STAFF_ADMIN';

export interface StaffUserSummary {
  id: number;
  email: string;
  globalRole: GlobalRole;
}

export interface GlobalAccessGroup {
  id: number;
  name: string;
}

export interface StaffUserDetail {
  userId: number;
  email: string;
  directPermissions: GlobalPermission[];
  accessGroups: GlobalAccessGroup[];
  effectivePermissions: GlobalPermission[];
}

@Injectable({ providedIn: 'root' })
export class StaffUserService {
  private readonly http = inject(HttpClient);

  list(email?: string): Observable<StaffUserSummary[]> {
    const params = email ? new HttpParams().set('email', email) : undefined;
    return this.http.get<StaffUserSummary[]>('/api/staff/users', { params });
  }

  create(email: string): Observable<StaffUserDetail> {
    return this.http.post<StaffUserDetail>('/api/staff/users', { email });
  }

  getDetail(userId: number): Observable<StaffUserDetail> {
    return this.http.get<StaffUserDetail>(`/api/staff/users/${userId}/permissions`);
  }

  grantPermission(userId: number, permission: GlobalPermission): Observable<void> {
    return this.http.post<void>(`/api/staff/users/${userId}/permissions`, { permission });
  }

  revokePermission(userId: number, permission: GlobalPermission): Observable<void> {
    return this.http.delete<void>(`/api/staff/users/${userId}/permissions/${permission}`);
  }

  listAccessGroups(): Observable<GlobalAccessGroup[]> {
    return this.http.get<GlobalAccessGroup[]>('/api/staff/access-groups');
  }

  createAccessGroup(name: string): Observable<GlobalAccessGroup> {
    return this.http.post<GlobalAccessGroup>('/api/staff/access-groups', { name });
  }

  grantAccessGroupPermission(
    accessGroupId: number,
    permission: GlobalPermission,
  ): Observable<void> {
    return this.http.post<void>(`/api/staff/access-groups/${accessGroupId}/permissions`, {
      permission,
    });
  }

  assignAccessGroup(userId: number, accessGroupId: number): Observable<void> {
    return this.http.post<void>(`/api/staff/users/${userId}/access-groups/${accessGroupId}`, {});
  }

  unassignAccessGroup(userId: number, accessGroupId: number): Observable<void> {
    return this.http.delete<void>(`/api/staff/users/${userId}/access-groups/${accessGroupId}`);
  }
}
