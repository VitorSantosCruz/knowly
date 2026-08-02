package br.com.conectabyte.knowly.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Distinct from {@link AddressDto} (all-optional, used by the direct-edit/self-request flows this
 * feature doesn't touch) -- every field here is required except {@code numero}/{@code complemento},
 * per specify/features/mandatory-complete-profile/SPEC.md's completeness definition.
 */
public record MandatoryAddressDto(
        @NotBlank String cep,
        @NotBlank String logradouro,
        String numero,
        String complemento,
        @NotBlank String bairro,
        @NotBlank String cidade,
        @NotBlank String estado,
        @NotBlank String pais) {}
