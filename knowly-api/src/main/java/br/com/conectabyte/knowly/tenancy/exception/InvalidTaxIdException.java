package br.com.conectabyte.knowly.tenancy.exception;

/**
 * A submitted {@code taxId} fails Brazil's CNPJ mod-11 checksum (400), per REQ-6c. Mirrors {@code
 * br.com.conectabyte.knowly.identity.exception.InvalidCpfException}'s shape exactly -- a bare
 * {@code RuntimeException}, no fields, so only the fixed {@code INVALID_TAX_ID} code is ever
 * returned, never the submitted value.
 */
public class InvalidTaxIdException extends RuntimeException {}
