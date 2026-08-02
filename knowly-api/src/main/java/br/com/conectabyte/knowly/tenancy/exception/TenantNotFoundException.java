package br.com.conectabyte.knowly.tenancy.exception;

/**
 * tenant-crud REQ-6/REQ-16: thrown by {@code editTenant}/{@code deleteTenant}/their
 * token-generation methods when the target tenant id doesn't exist, or exists but is already
 * soft-deleted -- a soft-deleted tenant is not editable/deletable back to life through these
 * endpoints.
 */
public class TenantNotFoundException extends RuntimeException {}
