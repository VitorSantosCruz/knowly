package br.com.conectabyte.knowly.identity.dto;

/**
 * {@code ProfileFieldsDto}'s fields plus {@code userId}, {@code email} (read-only) and {@code
 * avatarUrl} (read-only in this DTO -- only settable via the dedicated avatar upload endpoint,
 * REQ-10), per specify/features/identity-profile-model-v2/PLAN.md's API contracts.
 */
public record UserProfileDto(Long userId, String email, ProfileFieldsDto fields, String avatarUrl) {

    public static UserProfileDto of(
            Long userId, String email, ProfileFieldsDto fields, String avatarUrl) {
        return new UserProfileDto(userId, email, fields, avatarUrl);
    }
}
