import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Permission } from './permission';

export interface Member {
  membershipId: number;
  userId: number;
  email: string;
  role: 'ADMIN' | 'MEMBER';
}

export interface AccessGroup {
  id: number;
  name: string;
}

export interface MemberDetail extends Member {
  directPermissions: Permission[];
  accessGroups: AccessGroup[];
  effectivePermissions: Permission[];
}

@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);

  list(tenantId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`/api/tenants/${tenantId}/members`);
  }

  add(tenantId: number, email: string, role: 'ADMIN' | 'MEMBER'): Observable<void> {
    return this.http.post<void>(`/api/tenants/${tenantId}/members`, { email, role });
  }

  remove(tenantId: number, membershipId: number): Observable<void> {
    return this.http.delete<void>(`/api/tenants/${tenantId}/members/${membershipId}`);
  }

  getDetail(tenantId: number, membershipId: number): Observable<MemberDetail> {
    return this.http.get<MemberDetail>(`/api/tenants/${tenantId}/members/${membershipId}`);
  }

  grantPermission(
    tenantId: number,
    membershipId: number,
    permission: Permission,
  ): Observable<void> {
    return this.http.post<void>(`/api/tenants/${tenantId}/members/${membershipId}/permissions`, {
      permission,
    });
  }

  revokePermission(
    tenantId: number,
    membershipId: number,
    permission: Permission,
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/tenants/${tenantId}/members/${membershipId}/permissions/${permission}`,
    );
  }

  listAccessGroups(tenantId: number): Observable<AccessGroup[]> {
    return this.http.get<AccessGroup[]>(`/api/tenants/${tenantId}/access-groups`);
  }

  createAccessGroup(tenantId: number, name: string): Observable<void> {
    return this.http.post<void>(`/api/tenants/${tenantId}/access-groups`, { name });
  }

  assignAccessGroup(
    tenantId: number,
    membershipId: number,
    accessGroupId: number,
  ): Observable<void> {
    return this.http.post<void>(
      `/api/tenants/${tenantId}/members/${membershipId}/access-groups/${accessGroupId}`,
      {},
    );
  }

  unassignAccessGroup(
    tenantId: number,
    membershipId: number,
    accessGroupId: number,
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/tenants/${tenantId}/members/${membershipId}/access-groups/${accessGroupId}`,
    );
  }
}
