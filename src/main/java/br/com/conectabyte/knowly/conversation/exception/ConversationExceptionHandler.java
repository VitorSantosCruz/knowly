package br.com.conectabyte.knowly.conversation.exception;

import br.com.conectabyte.knowly.conversation.dto.ConversationErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ConversationExceptionHandler {

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ConversationErrorResponseDto> handleConversationNotFound(
            ConversationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ConversationErrorResponseDto("CONVERSATION_NOT_FOUND"));
    }
}
