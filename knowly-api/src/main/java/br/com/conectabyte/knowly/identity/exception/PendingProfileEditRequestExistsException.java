package br.com.conectabyte.knowly.identity.exception;

/** REQ-20: a requester with an already-unresolved ProfileEditRequest submits a new one. */
public class PendingProfileEditRequestExistsException extends RuntimeException {}
