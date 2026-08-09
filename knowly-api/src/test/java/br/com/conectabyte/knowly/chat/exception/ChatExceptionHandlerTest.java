package br.com.conectabyte.knowly.chat.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ChatExceptionHandlerTest {

    private final ChatExceptionHandler handler = new ChatExceptionHandler();

    @Test
    void groupStateConflictMapsToConflictWithDetail() {
        var response =
                handler.handleGroupStateConflict(
                        new ChatGroupStateConflictException(
                                ChatGroupStateConflictException.Detail.ARCHIVED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).contains("ARCHIVED");
    }

    @Test
    void duplicateParticipantMapsToForbidden() {
        var response = handler.handleDuplicateParticipant(new ChatDuplicateParticipantException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("CHAT_PARTICIPANT_ALREADY_MEMBER");
    }

    @Test
    void joinRequestConflictMapsToConflictWithDetail() {
        var response =
                handler.handleJoinRequestConflict(
                        new ChatJoinRequestConflictException(
                                ChatJoinRequestConflictException.Detail.ALREADY_DECIDED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).contains("ALREADY_DECIDED");
    }

    @Test
    void visibilityUnchangedMapsToBadRequest() {
        var response = handler.handleVisibilityUnchanged(new ChatVisibilityUnchangedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CHAT_VISIBILITY_UNCHANGED");
    }

    @Test
    void adminAlreadyGrantedMapsToBadRequest() {
        var response = handler.handleAdminAlreadyGranted(new ChatAdminAlreadyGrantedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CHAT_PARTICIPANT_ALREADY_ADMIN");
    }
}
