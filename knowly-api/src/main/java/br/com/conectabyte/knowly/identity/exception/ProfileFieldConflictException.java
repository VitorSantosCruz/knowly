package br.com.conectabyte.knowly.identity.exception;

/**
 * REQ-21: approving a ProfileEditRequest whose proposed field values would violate the global
 * uniqueness constraints (cpf/rg blind index, phone, address).
 */
public class ProfileFieldConflictException extends RuntimeException {}
