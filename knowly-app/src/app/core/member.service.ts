import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { Permission } from './permission';

export interface Member {
  membershipId: number;
  userId: number;
  email: string;
  role: 'MEMBER_ADMIN' | 'MEMBER';
}

export interface AccessGroup {
  id: number;
  name: string;
}

export interface MemberDetail extends Member {
  directPermissions: Permission[];
  accessGroups: AccessGroup[];
  effectivePermissions: Permission[];
  isLastAdminOfType: boolean;
}

@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly http = inject(HttpClient);

  list(tenantId: number): Observable<Member[]> {
    return this.http.get<Member[]>(`/api/tenants/${tenantId}/members`);
  }

  add(tenantId: number, email: string, role: 'MEMBER_ADMIN' | 'MEMBER'): Observable<void> {
    return this.http.post<void>(`/api/tenants/${tenantId}/members`, { email, role });
  }

  remove(tenantId: number, membershipId: number, word: string): Observable<void> {
    return this.http.delete<void>(`/api/tenants/${tenantId}/members/${membershipId}`, {
      body: { word },
    });
  }

  generateRemovalToken(tenantId: number, membershipId: number): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/tenants/${tenantId}/members/${membershipId}/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
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
    word: string,
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/tenants/${tenantId}/members/${membershipId}/permissions/${permission}`,
      { body: { word } },
    );
  }

  generatePermissionRevocationToken(
    tenantId: number,
    membershipId: number,
    permission: Permission,
  ): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/tenants/${tenantId}/members/${membershipId}/permissions/${permission}/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
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
    word: string,
  ): Observable<void> {
    return this.http.delete<void>(
      `/api/tenants/${tenantId}/members/${membershipId}/access-groups/${accessGroupId}`,
      { body: { word } },
    );
  }

  generateAccessGroupUnassignmentToken(
    tenantId: number,
    membershipId: number,
    accessGroupId: number,
  ): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/tenants/${tenantId}/members/${membershipId}/access-groups/${accessGroupId}/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
  }

  demote(tenantId: number, membershipId: number): Observable<void> {
    return this.http.post<void>(`/api/tenants/${tenantId}/members/${membershipId}/demote`, {});
  }

  promote(tenantId: number, membershipId: number): Observable<void> {
    return this.http.post<void>(`/api/tenants/${tenantId}/members/${membershipId}/promote`, {});
  }

  generateHardDeleteToken(tenantId: number, membershipId: number): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/tenants/${tenantId}/members/${membershipId}/hard-delete/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
  }

  hardDelete(tenantId: number, membershipId: number, word: string): Observable<void> {
    return this.http.delete<void>(`/api/tenants/${tenantId}/members/${membershipId}/hard-delete`, {
      body: { word },
    });
  }

  generateBatchPermissionUpdateToken(tenantId: number, membershipId: number): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/tenants/${tenantId}/members/${membershipId}/permissions/batch/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
  }

  batchUpdatePermissions(
    tenantId: number,
    membershipId: number,
    permissions: Permission[],
    word: string,
  ): Observable<void> {
    return this.http.put<void>(
      `/api/tenants/${tenantId}/members/${membershipId}/permissions/batch`,
      { permissions, word },
    );
  }
}
