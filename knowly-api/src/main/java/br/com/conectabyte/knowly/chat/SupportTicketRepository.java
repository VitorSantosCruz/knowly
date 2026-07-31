package br.com.conectabyte.knowly.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Optional<SupportTicket> findBySupportChannelIdAndStatusNot(
            Long supportChannelId, SupportTicketStatus status);

    List<SupportTicket> findBySupportChannelId(Long supportChannelId);

    List<SupportTicket> findByStatus(SupportTicketStatus status);
}
