package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatEnumsTest {

    @Test
    void chatConversationKindHasExactlyTheThreeShapes() {
        assertThat(ChatConversationKind.values())
                .containsExactly(
                        ChatConversationKind.PEER_DIRECT,
                        ChatConversationKind.PEER_GROUP,
                        ChatConversationKind.SUPPORT);
    }

    @Test
    void supportTicketStatusHasExactlyTheThreeStates() {
        assertThat(SupportTicketStatus.values())
                .containsExactly(
                        SupportTicketStatus.OPEN,
                        SupportTicketStatus.ASSIGNED,
                        SupportTicketStatus.CLOSED);
    }
}
