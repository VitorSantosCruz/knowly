package br.com.conectabyte.knowly.tenancy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.tenancy.Tenant;
import org.junit.jupiter.api.Test;

/**
 * tenant-creation: confirms Tenant's full-identification fields (legalName/taxId) round-trip
 * through TenantSummaryDto.
 */
class TenantSummaryDtoTest {

    @Test
    void fromIncludesTheNewIdentificationFields() {
        Tenant tenant = new Tenant("Acme");
        tenant.setLegalName("Acme Ltda");
        tenant.setTaxId("12345678000199");

        TenantSummaryDto dto = TenantSummaryDto.from(tenant);

        assertThat(dto.legalName()).isEqualTo("Acme Ltda");
        assertThat(dto.taxId()).isEqualTo("12345678000199");
    }
}
