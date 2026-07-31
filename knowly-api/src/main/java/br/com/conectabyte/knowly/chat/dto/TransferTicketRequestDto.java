package br.com.conectabyte.knowly.chat.dto;

import jakarta.validation.constraints.NotNull;

public record TransferTicketRequestDto(@NotNull Long toStaffUserId) {}
