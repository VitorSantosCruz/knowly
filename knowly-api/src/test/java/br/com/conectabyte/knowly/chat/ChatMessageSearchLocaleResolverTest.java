package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatMessageSearchLocaleResolverTest {

    private final ChatMessageSearchLocaleResolver resolver = new ChatMessageSearchLocaleResolver();

    @ParameterizedTest
    @ValueSource(strings = {"pt-BR", "pt-br", "PT-BR", "pt", "pt-PT", "pt-BR,en;q=0.9"})
    void resolvesPtVariantsToPt(String header) {
        assertThat(resolver.resolve(header)).isEqualTo(ChatSearchLocale.PT);
    }

    @NullAndEmptySource
    @ParameterizedTest
    void resolvesMissingOrEmptyHeaderToEn(String header) {
        assertThat(resolver.resolve(header)).isEqualTo(ChatSearchLocale.EN);
    }

    @ParameterizedTest
    @MethodSource("nonPtHeaders")
    void resolvesUnrelatedOrGarbageHeadersToEn(String header) {
        assertThat(resolver.resolve(header)).isEqualTo(ChatSearchLocale.EN);
    }

    private static Stream<Arguments> nonPtHeaders() {
        return Stream.of(
                arguments("en"),
                arguments("en-US"),
                arguments("es"),
                arguments("fr-FR,fr;q=0.9"),
                arguments("not a valid header !!!"),
                arguments("   "));
    }
}
