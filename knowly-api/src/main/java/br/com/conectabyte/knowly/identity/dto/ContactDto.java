package br.com.conectabyte.knowly.identity.dto;

import br.com.conectabyte.knowly.identity.ContactType;

/** REQ-3: one reachability channel row. */
public record ContactDto(
        Long id, ContactType type, String value, String label, boolean isPrimary) {}
