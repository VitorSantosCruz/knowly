package br.com.conectabyte.knowly.auth.dto;

/**
 * REQ-5 (specify/features/mandatory-complete-profile): surfaces whether the just-authenticated
 * account (in practice, only ever the bootstrap {@code STAFF_ADMIN}) must complete its profile
 * before anything else is usable -- computed once at login time and passed straight through, same
 * precedent as {@code staff-rbac-split}'s {@code isStaffAccount}.
 */
public record VerifyCodeResponseDto(boolean pendingProfileCompletion) {}
