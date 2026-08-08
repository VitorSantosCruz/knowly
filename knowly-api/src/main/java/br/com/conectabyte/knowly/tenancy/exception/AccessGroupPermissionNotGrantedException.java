package br.com.conectabyte.knowly.tenancy.exception;

/**
 * role-permission-revoke REQ-8: thrown by the access-group permission revoke endpoints (both
 * scopes) when the target role has no active grant of the given permission -- never granted, or
 * already revoked -- so a caller can distinguish "nothing to do" from "revoked," rather than the
 * silent no-op precedent used by direct-permission revoke (PLAN.md's "Architectural decisions").
 */
public class AccessGroupPermissionNotGrantedException extends RuntimeException {}
