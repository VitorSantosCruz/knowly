package br.com.conectabyte.knowly.chat.dto;

public record ChatParticipantRejectionDto(Long userId, Reason reason) {

    public enum Reason {
        ALREADY_PARTICIPANT,
        INELIGIBLE
    }
}
