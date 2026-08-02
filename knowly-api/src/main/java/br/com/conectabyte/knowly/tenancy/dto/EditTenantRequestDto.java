package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.Email;

/**
 * tenant-crud PLAN.md ("Architectural decisions"): partial-update DTO, every field optional -- a
 * field omitted (null) leaves the current value unchanged, a field present-but-blank is rejected
 * with 400 (REQ-2), enforced service-side since {@code @NotBlank} can't distinguish "omitted" from
 * "blank" on an optional field. {@code taxId} is deliberately not a field on this DTO at all
 * (REQ-3): immutability is enforced by never accepting it on the wire, not by accepting then
 * rejecting it.
 */
public record EditTenantRequestDto(
        String name,
        String legalName,
        @Email String contactEmail,
        String contactPhone,
        String postalCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state) {}
