package br.com.conectabyte.knowly.chat.exception;

/** REQ-11: a blank/missing/whitespace-only search {@code q} is always rejected. */
public class ChatBlankSearchQueryException extends RuntimeException {

    public ChatBlankSearchQueryException() {
        super("Search query must not be blank");
    }
}
