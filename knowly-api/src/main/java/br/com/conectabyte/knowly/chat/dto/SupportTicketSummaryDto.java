package br.com.conectabyte.knowly.chat.dto;

import br.com.conectabyte.knowly.chat.SupportTicket;
import java.time.Instant;

public record SupportTicketSummaryDto(
        Long id, Long supportChannelId, Long ownerUserId, String ownerNickname, Instant openedAt) {

    public static SupportTicketSummaryDto from(SupportTicket ticket, String ownerNickname) {
        var channel = ticket.getSupportChannel();
        return new SupportTicketSummaryDto(
                ticket.getId(),
                channel.getId(),
                channel.getOwner().getId(),
                ownerNickname,
                ticket.getOpenedAt());
    }
}
