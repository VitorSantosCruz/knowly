import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface TenantMembership {
  tenantId: number;
  tenantName: string;
  role: 'ADMIN' | 'MEMBER';
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class ActiveTenantService {
  private readonly http = inject(HttpClient);

  private readonly _activeTenantId = signal<number | null>(null);
  readonly activeTenantId = this._activeTenantId.asReadonly();

  private readonly _activeTenantName = signal<string | null>(null);
  readonly activeTenantName = this._activeTenantName.asReadonly();

  fetch(): void {
    this.list().subscribe((memberships) => {
      const active = memberships.find((membership) => membership.active);
      this._activeTenantId.set(active?.tenantId ?? null);
      this._activeTenantName.set(active?.tenantName ?? null);
    });
  }

  list(): Observable<TenantMembership[]> {
    return this.http.get<TenantMembership[]>('/api/tenants/memberships');
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
