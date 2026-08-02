package br.com.conectabyte.knowly.tenancy.exception;

/**
 * Thrown when a demote/delete would leave zero admins of a type ({@code STAFF_ADMIN} platform-wide
 * or {@code MEMBER_ADMIN} within a tenant) — see
 * specify/features/staff-rbac-management-operations/PLAN.md's pessimistic-lock floor check.
 */
public class LastAdminRemainingException extends RuntimeException {}
