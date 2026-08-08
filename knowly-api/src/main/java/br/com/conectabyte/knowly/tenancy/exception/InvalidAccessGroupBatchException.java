package br.com.conectabyte.knowly.tenancy.exception;

/**
 * tenant-access-group-bulk-and-delete REQ-3/REQ-4: thrown when a batch access-group assignment
 * request contains a duplicate id, or any id that doesn't resolve to a live {@code AccessGroup}
 * belonging to the calling tenant -- rejects the whole request, before any write.
 */
public class InvalidAccessGroupBatchException extends RuntimeException {}
