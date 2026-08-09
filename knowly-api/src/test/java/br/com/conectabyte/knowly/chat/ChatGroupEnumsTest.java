package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** chat-group-membership-management: new visibility/join-request enums. */
class ChatGroupEnumsTest {

    @Test
    void chatGroupVisibilityHasExactlyTheThreeModes() {
        assertThat(ChatGroupVisibility.values())
                .containsExactly(
                        ChatGroupVisibility.PRIVATE,
                        ChatGroupVisibility.REQUEST_TO_JOIN,
                        ChatGroupVisibility.PUBLIC);
    }

    @Test
    void chatJoinRequestStatusHasExactlyTheThreeStates() {
        assertThat(ChatJoinRequestStatus.values())
                .containsExactly(
                        ChatJoinRequestStatus.PENDING,
                        ChatJoinRequestStatus.APPROVED,
                        ChatJoinRequestStatus.REJECTED);
    }
}
