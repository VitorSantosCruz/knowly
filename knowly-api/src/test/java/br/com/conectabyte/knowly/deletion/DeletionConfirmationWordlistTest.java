package br.com.conectabyte.knowly.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DeletionConfirmationWordlistTest {

    @Autowired private DeletionConfirmationWordlist wordlist;

    @Test
    void enListHasAtLeastTheMinimumNumberOfEntries() {
        assertThat(wordlist.forLocale(DeletionLocale.EN).size())
                .isGreaterThanOrEqualTo(DeletionConfirmationWordlist.MIN_ENTRIES);
    }

    @Test
    void ptBrListHasAtLeastTheMinimumNumberOfEntries() {
        assertThat(wordlist.forLocale(DeletionLocale.PT_BR).size())
                .isGreaterThanOrEqualTo(DeletionConfirmationWordlist.MIN_ENTRIES);
    }

    @Test
    void enListHasNoDuplicateOrBlankEntriesAndEveryEntryMatchesTheFormatConstraint() {
        var words = wordlist.forLocale(DeletionLocale.EN);

        assertThat(words).doesNotHaveDuplicates();
        assertThat(words).allMatch(word -> word.matches("[a-z]{4,8}"));
    }

    @Test
    void ptBrListHasNoDuplicateOrBlankEntriesAndEveryEntryMatchesTheFormatConstraint() {
        var words = wordlist.forLocale(DeletionLocale.PT_BR);

        assertThat(words).doesNotHaveDuplicates();
        assertThat(words).allMatch(word -> word.matches("[a-z]{4,8}"));
    }
}
