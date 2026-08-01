import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface TenantMembership {
  tenantId: number;
  tenantName: string;
  role: 'MEMBER_ADMIN' | 'MEMBER';
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

  private readonly _activeTenantRole = signal<'MEMBER_ADMIN' | 'MEMBER' | null>(null);
  /** The viewer's own role within the active tenant — null for a staff session with no
   * real TenantMembership row, or once fetch() confirms there's no active tenant. */
  readonly activeTenantRole = this._activeTenantRole.asReadonly();

  private readonly _activeTenantResolved = signal(false);
  /** True once the first fetch() call has resolved, so callers can tell "still loading"
   * apart from "genuinely no active tenant" — both look like a null activeTenantId(). */
  readonly activeTenantResolved = this._activeTenantResolved.asReadonly();

  /**
   * True only while the current activeTenantId was set by selectTenant()'s optimistic
   * update and hasn't yet been confirmed by a real TenantMembership row from the backend
   * (a staff session acting as a tenant never gets one — only server-side session state,
   * so `list()` never reflects it for that case). fetch() only preserves the existing
   * signal value on this narrow race; a fetch() finding no active membership otherwise
   * (fresh page load/login, or a previously-real membership that's no longer active) must
   * null the signals out rather than carry a stale value across sessions.
   */
  private locallySelected = false;

  fetch(): void {
    this.list().subscribe((memberships) => {
      const active = memberships.find((membership) => membership.active);
      if (active) {
        this._activeTenantId.set(active.tenantId);
        this._activeTenantName.set(active.tenantName);
        this._activeTenantRole.set(active.role);
        this.locallySelected = false;
      } else if (!this.locallySelected) {
        this._activeTenantId.set(null);
        this._activeTenantName.set(null);
        this._activeTenantRole.set(null);
      }
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

  leaveTenant(): Observable<void> {
    return this.http.post<void>('/api/tenants/active/clear', {}).pipe(
      tap(() => {
        this._activeTenantId.set(null);
        this._activeTenantName.set(null);
        this._activeTenantRole.set(null);
        this.locallySelected = false;
      }),
    );
  }

  selectTenant(tenantId: number, tenantName: string): Observable<void> {
    return this.http.post<void>('/api/tenants/active', { tenantId }).pipe(
      tap(() => {
        this._activeTenantId.set(tenantId);
        this._activeTenantName.set(tenantName);
        this.locallySelected = true;
      }),
    );
  }
}
