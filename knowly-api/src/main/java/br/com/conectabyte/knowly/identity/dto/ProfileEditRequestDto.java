package br.com.conectabyte.knowly.identity.dto;

import br.com.conectabyte.knowly.identity.ProfileEditRequestStatus;
import java.time.Instant;

public record ProfileEditRequestDto(
        Long id,
        Long requesterUserId,
        ProfileFieldsDto proposedFields,
        ProfileEditRequestStatus status,
        Instant createdAt) {}
