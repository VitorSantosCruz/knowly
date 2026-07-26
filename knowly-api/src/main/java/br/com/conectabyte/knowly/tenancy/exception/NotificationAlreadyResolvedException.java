package br.com.conectabyte.knowly.tenancy.exception;

/**
 * REQ-11: the notification, or the {@code TenantMembership} it references, is no longer in the
 * state this action requires (already resolved, or the membership is no longer pending) — reject
 * rather than silently succeed or double-process.
 */
public class NotificationAlreadyResolvedException extends RuntimeException {}
