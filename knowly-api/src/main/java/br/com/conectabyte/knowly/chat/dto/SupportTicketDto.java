package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.SupportTicket;
import br.com.conectabyte.knowly.chat.SupportTicketStatus;
import java.time.Instant;

public record SupportTicketDto(
        Long id,
        Long supportChannelId,
        SupportTicketStatus status,
        Long assignedStaffUserId,
        Instant openedAt,
        Instant closedAt) {

    public static SupportTicketDto from(SupportTicket ticket) {
        return new SupportTicketDto(
                ticket.getId(),
                ticket.getSupportChannel().getId(),
                ticket.getStatus(),
                ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId(),
                ticket.getOpenedAt(),
                ticket.getClosedAt());
    }
}
