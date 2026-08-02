package br.com.conectabyte.knowly.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Distinct from {@link AddressDto} (all-optional, used by the direct-edit/self-request flows this
 * feature doesn't touch) -- every field here is required except {@code addressLine2}/{@code
 * stateRegion}, per specify/features/mandatory-complete-profile/SPEC.md's completeness definition
 * and the 2026-08-02 country-agnostic identity/address model amendment.
 */
public record MandatoryAddressDto(
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String city,
        String stateRegion,
        @NotBlank String postalCode,
        @NotBlank String countryCode) {}
