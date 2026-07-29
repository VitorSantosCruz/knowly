import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface TenantMembership {
  tenantId: number;
  tenantName: string;
  role: 'ADMIN' | 'MEMBER';
  active: boolean;
}

export interface TenantSummary {
  id: number;
  name: string;
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

  private readonly _activeTenantRole = signal<'ADMIN' | 'MEMBER' | null>(null);
  /** The viewer's own role within the active tenant — null for a staff session with no
   * real TenantMembership row, same "preserve on no active membership found" rule as
   * activeTenantId/activeTenantName below. */
  readonly activeTenantRole = this._activeTenantRole.asReadonly();

  private readonly _activeTenantResolved = signal(false);
  /** True once the first fetch() call has resolved, so callers can tell "still loading"
   * apart from "genuinely no active tenant" — both look like a null activeTenantId(). */
  readonly activeTenantResolved = this._activeTenantResolved.asReadonly();

  /**
   * A staff session acting as a tenant (via selectTenant()) never gets a real
   * TenantMembership row — only server-side session state — so this list never reflects
   * it. When no active *membership* is found, the existing signal value is preserved
   * rather than nulled out, so a staff session's already-known active tenant (set locally
   * by selectTenant() at the moment of switching) survives a later fetch() call instead of
   * being wiped back to null.
   */
  fetch(): void {
    this.list().subscribe((memberships) => {
      const active = memberships.find((membership) => membership.active);
      this._activeTenantId.set(active?.tenantId ?? this._activeTenantId());
      this._activeTenantName.set(active?.tenantName ?? this._activeTenantName());
      this._activeTenantRole.set(active?.role ?? this._activeTenantRole());
      this._activeTenantResolved.set(true);
    });
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

  createTenant(name: string, adminEmail: string): Observable<void> {
    return this.http.post<void>('/api/tenants', { name, adminEmail });
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
