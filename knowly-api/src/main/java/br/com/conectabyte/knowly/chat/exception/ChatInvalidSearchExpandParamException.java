package br.com.conectabyte.knowly.chat.exception;

/**
 * Unified entity search (2026-08-10 amendment): {@code type}+{@code offset} "see more" expand
 * params were supplied incompletely (one without the other) or {@code type} was outside the fixed
 * {@code people}/{@code groups}/{@code rag} enum.
 */
public class ChatInvalidSearchExpandParamException extends RuntimeException {

    public ChatInvalidSearchExpandParamException() {
        super("type and offset must both be supplied together, and type must be a known section");
    }
}
