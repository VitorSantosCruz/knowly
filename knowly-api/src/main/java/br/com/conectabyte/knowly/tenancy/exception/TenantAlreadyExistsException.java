package br.com.conectabyte.knowly.tenancy.exception;

/**
 * REQ-4/REQ-5 (tenant-creation): {@code taxId} collides with an existing tenant's, or {@code
 * adminEmail} is already in use by an existing account -- both are unique-constraint conflicts on
 * {@code POST /api/tenants}, disambiguated by which constraint fired (same pattern as {@link
 * StaffUserAlreadyExistsException}).
 */
public class TenantAlreadyExistsException extends RuntimeException {}
