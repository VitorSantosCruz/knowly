package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.chat.exception.ChatInvalidCursorException;
import org.junit.jupiter.api.Test;

class ChatCursorTest {

    @Test
    void encodeDecodeRoundTrips() {
        String cursor = ChatCursor.encode(42L);

        assertThat(ChatCursor.decode(cursor)).isEqualTo(42L);
    }

    @Test
    void decodeRejectsAMalformedCursor() {
        assertThatThrownBy(() -> ChatCursor.decode("not-valid-base64!"))
                .isInstanceOf(ChatInvalidCursorException.class);
    }

    @Test
    void clampSizeUsesDefaultWhenNullOrNonPositive() {
        assertThat(ChatCursor.clampSize(null)).isEqualTo(30);
        assertThat(ChatCursor.clampSize(0)).isEqualTo(30);
        assertThat(ChatCursor.clampSize(-5)).isEqualTo(30);
    }

    @Test
    void clampSizeCapsAboveTheMaximumInsteadOfRejecting() {
        assertThat(ChatCursor.clampSize(1000)).isEqualTo(100);
        assertThat(ChatCursor.clampSize(50)).isEqualTo(50);
    }
}
