import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { GlobalPermission } from './global-permission';
import { MandatoryProfileFields } from './profile.service';

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
  globalRole: GlobalRole;
  directPermissions: GlobalPermission[];
  accessGroups: GlobalAccessGroup[];
  effectivePermissions: GlobalPermission[];
  isLastAdminOfType: boolean;
}

export interface AuditEvent {
  occurredAt: string;
  action: string;
  resourceType: string;
  resourceId: string;
  tenantId: string | null;
  outcome: string;
  metadata: unknown;
}

@Injectable({ providedIn: 'root' })
export class StaffUserService {
  private readonly http = inject(HttpClient);

  list(email?: string): Observable<StaffUserSummary[]> {
    const params = email ? new HttpParams().set('email', email) : undefined;
    return this.http.get<StaffUserSummary[]>('/api/staff/users', { params });
  }

  // mandatory-complete-profile (backend): `profile` is a required field on this request —
  // omitting it 400s unconditionally, regardless of role.
  create(email: string, profile: MandatoryProfileFields): Observable<StaffUserDetail> {
    return this.http.post<StaffUserDetail>('/api/staff/users', { email, profile });
  }

  getDetail(userId: number): Observable<StaffUserDetail> {
    return this.http.get<StaffUserDetail>(`/api/staff/users/${userId}/permissions`);
  }

  grantPermission(userId: number, permission: GlobalPermission): Observable<void> {
    return this.http.post<void>(`/api/staff/users/${userId}/permissions`, { permission });
  }

  revokePermission(userId: number, permission: GlobalPermission, word: string): Observable<void> {
    return this.http.delete<void>(`/api/staff/users/${userId}/permissions/${permission}`, {
      body: { word },
    });
  }

  generatePermissionRevocationToken(
    userId: number,
    permission: GlobalPermission,
  ): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/staff/users/${userId}/permissions/${permission}/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
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

  unassignAccessGroup(userId: number, accessGroupId: number, word: string): Observable<void> {
    return this.http.delete<void>(`/api/staff/users/${userId}/access-groups/${accessGroupId}`, {
      body: { word },
    });
  }

  generateAccessGroupUnassignmentToken(userId: number, accessGroupId: number): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/staff/users/${userId}/access-groups/${accessGroupId}/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
  }

  getAuditTrail(userId: number): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`/api/staff/users/${userId}/audit-trail`);
  }

  demote(userId: number): Observable<void> {
    return this.http.post<void>(`/api/staff/users/${userId}/demote`, {});
  }

  promote(userId: number): Observable<void> {
    return this.http.post<void>(`/api/staff/users/${userId}/promote`, {});
  }

  generateDeletionConfirmationToken(userId: number): Observable<string> {
    return this.http
      .post<{ word: string }>(`/api/staff/users/${userId}/deletion-confirmation-token`, {})
      .pipe(map((res) => res.word));
  }

  delete(userId: number, word: string): Observable<void> {
    return this.http.delete<void>(`/api/staff/users/${userId}`, { body: { word } });
  }

  generateBatchPermissionUpdateToken(userId: number): Observable<string> {
    return this.http
      .post<{ word: string }>(
        `/api/staff/users/${userId}/permissions/batch/deletion-confirmation-token`,
        {},
      )
      .pipe(map((res) => res.word));
  }

  batchUpdatePermissions(
    userId: number,
    permissions: GlobalPermission[],
    word: string,
  ): Observable<void> {
    return this.http.put<void>(`/api/staff/users/${userId}/permissions/batch`, {
      permissions,
      word,
    });
  }
}
