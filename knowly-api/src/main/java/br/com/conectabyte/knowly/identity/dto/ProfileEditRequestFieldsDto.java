package br.com.conectabyte.knowly.identity.dto;

import java.util.List;

/** REQ-15: the submit-request request body -- flattened fields plus contact add/update/remove. */
public record ProfileEditRequestFieldsDto(
        ProfileFieldsDto fields, List<ContactChangeDto> contactChanges) {}
