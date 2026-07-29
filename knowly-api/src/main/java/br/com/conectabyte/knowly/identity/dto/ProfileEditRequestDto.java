package br.com.conectabyte.knowly.identity.dto;

import br.com.conectabyte.knowly.identity.ProfileEditRequestStatus;
import java.time.Instant;
import java.util.List;

public record ProfileEditRequestDto(
        Long id,
        Long requesterUserId,
        ProfileFieldsDto proposedFields,
        List<ContactChangeDto> proposedContactChanges,
        ProfileEditRequestStatus status,
        Instant createdAt) {}
