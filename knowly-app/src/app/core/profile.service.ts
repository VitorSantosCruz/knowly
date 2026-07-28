import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface ProfileFields {
  fullName: string;
  address: string;
  rg: string;
  cpf: string;
  phone: string;
}

export interface UserProfile extends ProfileFields {
  userId: number;
  email: string;
}

export type ProfileEditRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ProfileEditRequest {
  id: number;
  requesterUserId: number;
  proposedFields: ProfileFields;
  status: ProfileEditRequestStatus;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  getOwnProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>('/api/users/me/profile');
  }

  getProfile(userId: number): Observable<UserProfile> {
    return this.http.get<UserProfile>(`/api/users/${userId}/profile`);
  }

  directEdit(userId: number, fields: ProfileFields): Observable<UserProfile> {
    return this.http.put<UserProfile>(`/api/users/${userId}/profile`, fields);
  }

  submitEditRequest(fields: ProfileFields): Observable<ProfileEditRequest> {
    return this.http.post<ProfileEditRequest>('/api/users/me/profile/edit-requests', fields);
  }

  listEditRequests(): Observable<ProfileEditRequest[]> {
    return this.http.get<ProfileEditRequest[]>('/api/profile-edit-requests');
  }

  approveEditRequest(id: number): Observable<void> {
    return this.http.post<void>(`/api/profile-edit-requests/${id}/approve`, {});
  }

  rejectEditRequest(id: number): Observable<void> {
    return this.http.post<void>(`/api/profile-edit-requests/${id}/reject`, {});
  }
}
