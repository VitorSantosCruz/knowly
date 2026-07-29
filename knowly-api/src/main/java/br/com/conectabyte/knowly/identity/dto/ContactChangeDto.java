package br.com.conectabyte.knowly.identity.dto;

import br.com.conectabyte.knowly.identity.ContactChangeAction;
import br.com.conectabyte.knowly.identity.ContactType;

/** REQ-15: one proposed add/update/remove of a contact, part of a profile-edit request. */
public record ContactChangeDto(
        ContactChangeAction action,
        Long contactId,
        ContactType type,
        String value,
        String label,
        Boolean isPrimary) {}
