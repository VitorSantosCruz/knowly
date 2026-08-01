package br.com.conectabyte.knowly.deletion.exception;

/**
 * REQ-7: thrown when a supplied deletion confirmation word does not match an unexpired, unused
 * token for the exact resource instance and calling user — deliberately carries no distinguishing
 * detail about which failure mode occurred (wrong word, expired, already used, wrong resource,
 * wrong user).
 */
public class DeletionConfirmationInvalidException extends RuntimeException {}
