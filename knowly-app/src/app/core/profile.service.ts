import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type ContactType = 'PHONE' | 'WHATSAPP' | 'EMAIL' | 'OTHER';

export interface Contact {
  id: number | null;
  type: ContactType;
  value: string;
  label: string | null;
  isPrimary: boolean;
}

export interface Address {
  cep: string | null;
  logradouro: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string | null;
  estado: string | null;
  pais: string | null;
}

export interface ProfileFields {
  fullName: string | null;
  cpf: string | null;
  rg: string | null;
  rgOrgaoEmissor: string | null;
  birthDate: string | null;
  address: Address | null;
  contacts: Contact[];
}

// Deviation from PLAN.md: `UserProfileDto` (identity-profile-model-v2, as shipped) nests the
// editable fields under a `fields` object rather than flattening them alongside
// `userId`/`email`/`avatarUrl` — `UserProfile` therefore composes `ProfileFields` as a nested
// property instead of extending it, matching the real response shape byte-for-byte.
export interface UserProfile {
  userId: number;
  email: string;
  fields: ProfileFields;
  avatarUrl: string | null;
}

export type ContactChangeAction = 'ADD' | 'UPDATE' | 'REMOVE';

export interface ContactChange {
  action: ContactChangeAction;
  contactId: number | null;
  type: ContactType | null;
  value: string | null;
  label: string | null;
  isPrimary: boolean | null;
}

export type ProfileEditRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface ProfileEditRequest {
  id: number;
  requesterUserId: number;
  proposedFields: ProfileFields;
  proposedContactChanges: ContactChange[];
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

  // Deviation from PLAN.md resolved (knowly-api c0a817d): `PUT /api/users/{id}/profile` now
  // accepts the same `{fields, contactChanges}` wrapper as `submitEditRequest` and genuinely
  // applies the contact changes, so this method regains the second parameter PLAN.md anticipated.
  directEdit(
    userId: number,
    fields: ProfileFields,
    contactChanges: ContactChange[],
  ): Observable<UserProfile> {
    return this.http.put<UserProfile>(`/api/users/${userId}/profile`, { fields, contactChanges });
  }

  uploadAvatar(file: File): Observable<UserProfile> {
    const formData = new FormData();
    formData.set('file', file);

    return this.http.post<UserProfile>('/api/users/me/profile/avatar', formData);
  }

  submitEditRequest(
    fields: ProfileFields,
    contactChanges: ContactChange[],
  ): Observable<ProfileEditRequest> {
    return this.http.post<ProfileEditRequest>('/api/users/me/profile/edit-requests', {
      fields,
      contactChanges,
    });
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
