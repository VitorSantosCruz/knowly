package br.com.conectabyte.knowly.identity.exception;

/** REQ-3a: adding a contact would push a user past the 5-row cap (400). */
public class ContactCapExceededException extends RuntimeException {}
