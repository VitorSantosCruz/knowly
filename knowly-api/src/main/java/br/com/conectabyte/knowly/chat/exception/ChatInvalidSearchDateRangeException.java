package br.com.conectabyte.knowly.chat.exception;

/** REQ-12: {@code dateFrom} later than {@code dateTo} is always rejected. */
public class ChatInvalidSearchDateRangeException extends RuntimeException {

    public ChatInvalidSearchDateRangeException() {
        super("dateFrom must not be after dateTo");
    }
}
