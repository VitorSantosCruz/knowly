package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Tenant;

public record TenantSummaryDto(Long id, String name) {

    public static TenantSummaryDto from(Tenant tenant) {
        return new TenantSummaryDto(tenant.getId(), tenant.getName());
    }
}
