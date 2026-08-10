package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatSearchLocaleTest {

    @Test
    void hasExactlyPtAndEn() {
        assertThat(ChatSearchLocale.values())
                .containsExactly(ChatSearchLocale.PT, ChatSearchLocale.EN);
    }
}
