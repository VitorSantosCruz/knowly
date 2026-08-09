package br.com.conectabyte.knowly.chat.exception;

import lombok.Getter;

/** REQ-34/36: duplicate pending join request, or an approve/reject targeting a decided one. */
@Getter
public class ChatJoinRequestConflictException extends RuntimeException {

    public enum Detail {
        DUPLICATE_PENDING,
        ALREADY_DECIDED
    }

    private final Detail detail;

    public ChatJoinRequestConflictException(Detail detail) {
        this.detail = detail;
    }
}
