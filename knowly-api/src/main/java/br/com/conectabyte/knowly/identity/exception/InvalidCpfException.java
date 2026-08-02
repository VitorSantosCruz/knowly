package br.com.conectabyte.knowly.identity.exception;

/** A submitted {@code cpf} fails the mod-11 checksum (400), per REQ-4a. */
public class InvalidCpfException extends RuntimeException {}
