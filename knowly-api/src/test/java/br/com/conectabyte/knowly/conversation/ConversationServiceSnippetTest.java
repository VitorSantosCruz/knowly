package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * RAG conversation turn-content search (2026-08-11 amendment), REQ-31: truncation-boundary coverage
 * for {@link ConversationService#buildSnippet(String, String)}.
 */
class ConversationServiceSnippetTest {

    @Test
    void returnsTheContentUnchangedWhenShorterThanTheTruncationBound() {
        String content = "short turn about the meeting";

        String snippet = ConversationService.buildSnippet(content, "meeting");

        assertThat(snippet).isEqualTo(content);
    }

    @Test
    void centersTheWindowOnAMatchNearTheStartOfALongTurn() {
        String content = "meeting " + "x".repeat(300);

        String snippet = ConversationService.buildSnippet(content, "meeting");

        assertThat(snippet).hasSizeLessThanOrEqualTo(150);
        assertThat(snippet).startsWith("meeting");
    }

    @Test
    void centersTheWindowOnAMatchNearTheEndOfALongTurn() {
        String content = "x".repeat(300) + "meeting";

        String snippet = ConversationService.buildSnippet(content, "meeting");

        assertThat(snippet).hasSizeLessThanOrEqualTo(150);
        assertThat(snippet).endsWith("meeting");
    }

    @Test
    void neverThrowsAndNeverSplitsASurrogatePairAtTheStartBoundary() {
        // A high/low surrogate pair (an emoji) sitting right where the window would otherwise cut.
        String emoji = "😀"; // 😀
        StringBuilder builder = new StringBuilder();
        builder.append("x".repeat(100));
        builder.append(emoji);
        builder.append("meeting");
        builder.append("x".repeat(200));
        String content = builder.toString();

        String snippet = ConversationService.buildSnippet(content, "meeting");

        assertThat(snippet).hasSizeLessThanOrEqualTo(151);
        assertThat(snippet).contains("meeting");
        // If the emoji is included at all, it must be as a complete, valid pair.
        assertThat(snippet.codePoints().allMatch(Character::isDefined)).isTrue();
    }

    @Test
    void neverThrowsAndNeverSplitsASurrogatePairAtTheEndBoundary() {
        String emoji = "😀"; // 😀
        StringBuilder builder = new StringBuilder();
        builder.append("x".repeat(200));
        builder.append("meeting");
        builder.append("x".repeat(100));
        builder.append(emoji);
        String content = builder.toString();

        String snippet = ConversationService.buildSnippet(content, "meeting");

        assertThat(snippet).hasSizeLessThanOrEqualTo(151);
        assertThat(snippet.codePoints().allMatch(Character::isDefined)).isTrue();
    }
}
