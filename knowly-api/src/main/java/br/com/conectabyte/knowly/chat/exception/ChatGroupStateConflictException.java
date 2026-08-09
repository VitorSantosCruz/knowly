package br.com.conectabyte.knowly.chat.exception;

import lombok.Getter;

/**
 * 409-shaped "conversation exists but is not currently actionable" -- one exception type with a
 * {@link Detail} enum rather than four separate exception classes, since all four share the same
 * HTTP semantic (see PLAN.md's API contracts section).
 */
@Getter
public class ChatGroupStateConflictException extends RuntimeException {

    public enum Detail {
        NOT_PEER_GROUP,
        ARCHIVED,
        ALREADY_DELETED,
        WRONG_VISIBILITY_MODE,
        WOULD_EMPTY_GROUP
    }

    private final Detail detail;

    public ChatGroupStateConflictException(Detail detail) {
        this.detail = detail;
    }
}
