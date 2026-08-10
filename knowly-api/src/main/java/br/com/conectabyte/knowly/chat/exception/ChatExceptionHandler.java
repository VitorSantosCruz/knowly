package br.com.conectabyte.knowly.chat.exception;

import br.com.conectabyte.knowly.chat.dto.ChatErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ChatExceptionHandler {

    @ExceptionHandler(ChatAccessDeniedException.class)
    public ResponseEntity<ChatErrorResponseDto> handleAccessDenied(ChatAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ChatErrorResponseDto("CHAT_ACCESS_DENIED"));
    }

    @ExceptionHandler(ChatIneligibleParticipantException.class)
    public ResponseEntity<ChatErrorResponseDto> handleIneligibleParticipant(
            ChatIneligibleParticipantException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_INELIGIBLE_PARTICIPANT"));
    }

    @ExceptionHandler(ChatConversationNotFoundException.class)
    public ResponseEntity<ChatErrorResponseDto> handleNotFound(
            ChatConversationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ChatErrorResponseDto("CHAT_CONVERSATION_NOT_FOUND"));
    }

    @ExceptionHandler(SupportTicketConflictException.class)
    public ResponseEntity<ChatErrorResponseDto> handleTicketConflict(
            SupportTicketConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ChatErrorResponseDto("SUPPORT_TICKET_CONFLICT"));
    }

    /**
     * Covers a malformed/tampered pagination cursor ({@link
     * br.com.conectabyte.knowly.chat.ChatCursor#decode}) -- a client-supplied value that fails to
     * decode is a bad request, never a server error. Scoped to {@link ChatInvalidCursorException}
     * specifically (not the broader {@code IllegalArgumentException}, which -- since this advice is
     * application-wide -- previously caught and mislabeled unrelated failures from other modules;
     * see that exception's javadoc).
     */
    @ExceptionHandler(ChatInvalidCursorException.class)
    public ResponseEntity<ChatErrorResponseDto> handleMalformedCursor(
            ChatInvalidCursorException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_INVALID_CURSOR"));
    }

    @ExceptionHandler(ChatGroupStateConflictException.class)
    public ResponseEntity<ChatErrorResponseDto> handleGroupStateConflict(
            ChatGroupStateConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ChatErrorResponseDto("CHAT_GROUP_STATE_CONFLICT:" + ex.getDetail()));
    }

    @ExceptionHandler(ChatDuplicateParticipantException.class)
    public ResponseEntity<ChatErrorResponseDto> handleDuplicateParticipant(
            ChatDuplicateParticipantException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ChatErrorResponseDto("CHAT_PARTICIPANT_ALREADY_MEMBER"));
    }

    @ExceptionHandler(ChatJoinRequestConflictException.class)
    public ResponseEntity<ChatErrorResponseDto> handleJoinRequestConflict(
            ChatJoinRequestConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ChatErrorResponseDto("CHAT_JOIN_REQUEST_" + ex.getDetail()));
    }

    @ExceptionHandler(ChatVisibilityUnchangedException.class)
    public ResponseEntity<ChatErrorResponseDto> handleVisibilityUnchanged(
            ChatVisibilityUnchangedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_VISIBILITY_UNCHANGED"));
    }

    @ExceptionHandler(ChatAdminAlreadyGrantedException.class)
    public ResponseEntity<ChatErrorResponseDto> handleAdminAlreadyGranted(
            ChatAdminAlreadyGrantedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_PARTICIPANT_ALREADY_ADMIN"));
    }

    @ExceptionHandler(ChatBlankSearchQueryException.class)
    public ResponseEntity<ChatErrorResponseDto> handleBlankSearchQuery(
            ChatBlankSearchQueryException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_SEARCH_QUERY_BLANK"));
    }

    @ExceptionHandler(ChatInvalidSearchDateRangeException.class)
    public ResponseEntity<ChatErrorResponseDto> handleInvalidSearchDateRange(
            ChatInvalidSearchDateRangeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_SEARCH_INVALID_DATE_RANGE"));
    }

    @ExceptionHandler(ChatInvalidSearchExpandParamException.class)
    public ResponseEntity<ChatErrorResponseDto> handleInvalidSearchExpandParam(
            ChatInvalidSearchExpandParamException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ChatErrorResponseDto("CHAT_SEARCH_INVALID_EXPAND_PARAM"));
    }
}
