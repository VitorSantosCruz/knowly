import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface TenantMembership {
  tenantId: number;
  tenantName: string;
  role: 'MEMBER_ADMIN' | 'MEMBER';
  active: boolean;
}

export interface ActiveTenant {
  tenantId: number;
  tenantName: string;
  /** Omitted by the backend for a staff session acting as a tenant with no real membership row. */
  role?: 'MEMBER_ADMIN' | 'MEMBER';
}

export interface TenantSummary {
  id: number;
  name: string;
}

export interface CreateTenantAddress {
  postalCode: string;
  street: string;
  number: string;
  complement: string | null;
  neighborhood: string;
  city: string;
  state: string;
}

// user-profile-v2 amendment (2026-08-02, "country-agnostic identity/address model"): mirrors
// `MandatoryProfileFieldsDto`'s own restructuring (backend PLAN.md task 37) — same country-
// agnostic 6-field address shape as `core/profile.service.ts`'s `Address`, `cpf` renamed
// `taxId`, `rg`/`rgOrgaoEmissor`/`birthDate` removed entirely (RG/birth_date removal amendments).
export interface CreateTenantMandatoryAddress {
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  stateRegion: string | null;
  postalCode: string;
  countryCode: string;
}

export interface CreateTenantContact {
  type: 'EMAIL' | 'PHONE' | 'WHATSAPP' | 'OTHER';
  value: string;
}

export interface CreateTenantProfile {
  fullName: string;
  taxId: string;
  countryCode: string;
  address: CreateTenantMandatoryAddress;
  contacts: CreateTenantContact[];
}

/**
 * Matches backend `CreateTenantRequestDto` field for field (see
 * knowly-api/specify/features/tenant-creation/PLAN.md's "Consumed API contracts") — the company
 * address uses AddressDto's English names, the first user's address uses
 * MandatoryAddressDto's Portuguese names; these genuinely differ, not a typo.
 */
export interface CreateTenantRequest {
  name: string;
  legalName: string;
  taxId: string;
  country: string;
  contactEmail: string;
  contactPhone: string;
  address: CreateTenantAddress;
  adminEmail: string;
  profile: CreateTenantProfile;
  role: 'MEMBER' | 'MEMBER_ADMIN';
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class ActiveTenantService {
  private readonly http = inject(HttpClient);

  private readonly _activeTenantId = signal<number | null>(null);
  readonly activeTenantId = this._activeTenantId.asReadonly();

  private readonly _activeTenantName = signal<string | null>(null);
  readonly activeTenantName = this._activeTenantName.asReadonly();

  private readonly _activeTenantRole = signal<'MEMBER_ADMIN' | 'MEMBER' | null>(null);
  /** The viewer's own role within the active tenant — null for a staff session with no
   * real TenantMembership row, or once fetch() confirms there's no active tenant. */
  readonly activeTenantRole = this._activeTenantRole.asReadonly();

  private readonly _activeTenantResolved = signal(false);
  /** True once the first fetch() call has resolved, so callers can tell "still loading"
   * apart from "genuinely no active tenant" — both look like a null activeTenantId(). */
  readonly activeTenantResolved = this._activeTenantResolved.asReadonly();

  /**
   * fetch() used to derive the active tenant from list()'s TenantMembership rows, which a
   * staff session acting as a tenant never has one of (server-side session state only) — a
   * `locallySelected` flag used to work around that by preserving selectTenant()'s optimistic
   * value across a fetch() that legitimately found nothing. GET /api/tenants/active (below)
   * reads TenantContext directly, the same source selectTenant()/leaveTenant() write to, so it
   * is authoritative for both the staff and regular-member cases and that workaround is no
   * longer needed — removed rather than kept as dead code.
   */
  fetch(): void {
    this.getActive().subscribe((active) => {
      if (active) {
        this._activeTenantId.set(active.tenantId);
        this._activeTenantName.set(active.tenantName);
        this._activeTenantRole.set(active.role ?? null);
      } else {
        this._activeTenantId.set(null);
        this._activeTenantName.set(null);
        this._activeTenantRole.set(null);
      }
      this._activeTenantResolved.set(true);
    });
  }

  /**
   * The caller's session-derived active tenant, straight from TenantContext server-side —
   * works for a staff session acting as a tenant (no real TenantMembership row, `role`
   * omitted by the backend) just as well as a regular member's real membership. Angular's
   * HttpClient resolves a 204 (no active tenant) to a null body, so no special-casing needed
   * beyond the return type.
   */
  getActive(): Observable<ActiveTenant | null> {
    return this.http.get<ActiveTenant | null>('/api/tenants/active');
  }

  list(): Observable<TenantMembership[]> {
    return this.http.get<TenantMembership[]>('/api/tenants/memberships');
  }

  listAllTenants(
    page: number,
    size: number,
    search?: string,
  ): Observable<PageResponse<TenantSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<PageResponse<TenantSummary>>('/api/tenants', { params });
  }

  createTenant(request: CreateTenantRequest): Observable<void> {
    return this.http.post<void>('/api/tenants', request);
  }

  leaveTenant(): Observable<void> {
    return this.http.post<void>('/api/tenants/active/clear', {}).pipe(
      tap(() => {
        this._activeTenantId.set(null);
        this._activeTenantName.set(null);
        this._activeTenantRole.set(null);
      }),
    );
  }

  selectTenant(tenantId: number, tenantName: string): Observable<void> {
    return this.http.post<void>('/api/tenants/active', { tenantId }).pipe(
      tap(() => {
        this._activeTenantId.set(tenantId);
        this._activeTenantName.set(tenantName);
      }),
    );
  }
}
