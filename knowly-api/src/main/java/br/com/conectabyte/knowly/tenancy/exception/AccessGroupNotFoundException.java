package br.com.conectabyte.knowly.tenancy.exception;

/**
 * tenant-access-group-bulk-and-delete REQ-15: thrown by the access-group delete/token-generation
 * endpoints when the target access-group id doesn't exist, belongs to a different tenant, or is
 * already soft-deleted -- unlike every other {@code AccessGroup}-id lookup in this codebase ({@code
 * TenantAccessDeniedException}, 403, existence-hiding), REQ-15 explicitly pins 404 for this exact
 * endpoint (PLAN.md's "Architectural decisions").
 */
public class AccessGroupNotFoundException extends RuntimeException {}
