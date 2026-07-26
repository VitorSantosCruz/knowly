package br.com.conectabyte.knowly.tenancy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.tenancy.Tenant;
import org.junit.jupiter.api.Test;

/**
 * Task 21 (identity-profile-model): confirms Tenant's new company-record fields
 * (cnpj/razaoSocial/nomeFantasia/inscricaoEstadual) round-trip through TenantSummaryDto.
 */
class TenantSummaryDtoTest {

    @Test
    void fromIncludesTheNewCompanyRecordFields() {
        Tenant tenant = new Tenant("Acme");
        tenant.setCnpj("12.345.678/0001-99");
        tenant.setRazaoSocial("Acme Ltda");
        tenant.setNomeFantasia("Acme");
        tenant.setInscricaoEstadual("123456789");

        TenantSummaryDto dto = TenantSummaryDto.from(tenant);

        assertThat(dto.cnpj()).isEqualTo("12.345.678/0001-99");
        assertThat(dto.razaoSocial()).isEqualTo("Acme Ltda");
        assertThat(dto.nomeFantasia()).isEqualTo("Acme");
        assertThat(dto.inscricaoEstadual()).isEqualTo("123456789");
    }
}
