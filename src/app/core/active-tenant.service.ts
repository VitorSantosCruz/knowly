import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

interface TenantMembership {
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
    this.http.get<TenantMembership[]>('/api/tenants/memberships').subscribe((memberships) => {
      const active = memberships.find((membership) => membership.active);
      this._activeTenantId.set(active?.tenantId ?? null);
      this._activeTenantName.set(active?.tenantName ?? null);
    });
  }
}
