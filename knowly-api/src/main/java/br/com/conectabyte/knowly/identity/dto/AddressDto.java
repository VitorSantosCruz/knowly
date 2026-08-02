package br.com.conectabyte.knowly.identity.dto;

/** REQ-2a: a structured, single current address, country-agnostic shape. */
public record AddressDto(
        String addressLine1,
        String addressLine2,
        String city,
        String stateRegion,
        String postalCode,
        String countryCode) {}
