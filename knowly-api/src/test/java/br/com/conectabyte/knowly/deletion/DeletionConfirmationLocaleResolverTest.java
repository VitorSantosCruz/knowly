package br.com.conectabyte.knowly.deletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DeletionConfirmationLocaleResolverTest {

    private final DeletionConfirmationLocaleResolver resolver =
            new DeletionConfirmationLocaleResolver();

    @ParameterizedTest
    @ValueSource(strings = {"pt-BR", "pt-br", "PT-BR", "pt", "pt-BR,en;q=0.9"})
    void resolvesPtBrVariantsToPtBr(String header) {
        assertThat(resolver.resolve(header)).isEqualTo(DeletionLocale.PT_BR);
    }

    @NullAndEmptySource
    @ParameterizedTest
    void resolvesMissingOrEmptyHeaderToEn(String header) {
        assertThat(resolver.resolve(header)).isEqualTo(DeletionLocale.EN);
    }

    @ParameterizedTest
    @MethodSource("nonPtHeaders")
    void resolvesUnrelatedOrGarbageHeadersToEn(String header) {
        assertThat(resolver.resolve(header)).isEqualTo(DeletionLocale.EN);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> nonPtHeaders() {
        return Stream.of(
                arguments("en"),
                arguments("en-US"),
                arguments("es"),
                arguments("fr-FR,fr;q=0.9"),
                arguments("not a valid header !!!"),
                arguments("   "));
    }
}
