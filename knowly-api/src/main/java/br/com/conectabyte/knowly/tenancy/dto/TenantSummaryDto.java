package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.Tenant;
import java.time.Instant;

/**
 * tenant-crud PLAN.md ("API contracts"): widened with every editable identification field plus
 * {@code deletedAt} rather than introducing a separate {@code TenantDetailDto} -- the field sets
 * are identical (this DTO already backs both {@code listAllTenants} and the new {@code
 * listDeactivatedTenants}/{@code editTenant} responses). {@code deletedAt} is {@code null} for
 * every row returned by the active listing/edit response, populated only for {@code
 * listDeactivatedTenants}'s rows (REQ-20).
 */
public record TenantSummaryDto(
        Long id,
        String name,
        String legalName,
        String taxId,
        String country,
        String contactEmail,
        String contactPhone,
        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        Instant deletedAt) {

    public static TenantSummaryDto from(Tenant tenant) {
        return new TenantSummaryDto(
                tenant.getId(),
                tenant.getName(),
                tenant.getLegalName(),
                tenant.getTaxId(),
                tenant.getCountry(),
                tenant.getContactEmail(),
                tenant.getContactPhone(),
                tenant.getPostalCode(),
                tenant.getStreet(),
                tenant.getNumber(),
                tenant.getComplement(),
                tenant.getNeighborhood(),
                tenant.getCity(),
                tenant.getState(),
                tenant.getDeletedAt());
    }
}
