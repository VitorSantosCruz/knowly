package br.com.conectabyte.knowly.chat.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatSearchExceptionsTest {

    @Test
    void blankSearchQueryExceptionIsARuntimeException() {
        assertThat(new ChatBlankSearchQueryException()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidSearchDateRangeExceptionIsARuntimeException() {
        assertThat(new ChatInvalidSearchDateRangeException()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidSearchExpandParamExceptionIsARuntimeException() {
        assertThat(new ChatInvalidSearchExpandParamException())
                .isInstanceOf(RuntimeException.class);
    }
}
