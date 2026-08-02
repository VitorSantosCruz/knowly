package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.NotBlank;

/** REQ-1/REQ-2: the company's own structured address, distinct from a member's personal one. */
public record AddressDto(
        @NotBlank String postalCode,
        @NotBlank String street,
        @NotBlank String number,
        String complement,
        @NotBlank String neighborhood,
        @NotBlank String city,
        @NotBlank String state) {}
