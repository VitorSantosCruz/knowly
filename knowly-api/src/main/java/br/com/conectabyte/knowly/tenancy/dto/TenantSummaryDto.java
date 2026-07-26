package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Tenant;

public record TenantSummaryDto(
        Long id,
        String name,
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String inscricaoEstadual) {

    public static TenantSummaryDto from(Tenant tenant) {
        return new TenantSummaryDto(
                tenant.getId(),
                tenant.getName(),
                tenant.getCnpj(),
                tenant.getRazaoSocial(),
                tenant.getNomeFantasia(),
                tenant.getInscricaoEstadual());
    }
}
