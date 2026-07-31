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
}
