package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.chat.exception.ChatInvalidCursorException;
import java.util.Base64;

/**
 * Opaque {@code base64(String.valueOf(id))} cursor, per PLAN's id-only cursor decision (REQ-20/21).
 */
public final class ChatCursor {

    public static final int DEFAULT_PAGE_SIZE = 30;
    public static final int MAX_PAGE_SIZE = 100;

    private ChatCursor() {}

    public static String encode(long id) {
        return Base64.getEncoder().encodeToString(String.valueOf(id).getBytes());
    }

    public static long decode(String cursor) {
        try {
            return Long.parseLong(new String(Base64.getDecoder().decode(cursor)));
        } catch (IllegalArgumentException ex) {
            throw new ChatInvalidCursorException(ex);
        }
    }

    public static int clampSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }
}
