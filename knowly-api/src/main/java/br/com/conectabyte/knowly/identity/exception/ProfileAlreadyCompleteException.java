package br.com.conectabyte.knowly.identity.exception;

/**
 * REQ-6: {@code POST /api/users/me/profile/complete} only ever applies to a caller's *first*
 * completion -- calling it again once the profile is already complete is rejected outright, never
 * silently overwriting an already-set field (that would bypass identity-profile-model-v2's
 * self-request/approval requirement for changes).
 */
public class ProfileAlreadyCompleteException extends RuntimeException {}
