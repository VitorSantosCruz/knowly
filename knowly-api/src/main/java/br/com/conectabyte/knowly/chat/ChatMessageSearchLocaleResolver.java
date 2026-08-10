package br.com.conectabyte.knowly.chat;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * REQ-13/15: resolves the caller's search locale from the raw {@code Accept-Language} header value
 * -- byte-for-byte identical resolution logic to {@code
 * br.com.conectabyte.knowly.deletion.DeletionConfirmationLocaleResolver}, deliberately duplicated
 * rather than reused so this feature's {@link ChatSearchLocale} isn't coupled to that unrelated
 * feature's locale type (see PLAN.md's "Architectural decisions"). Not registered as a Spring
 * {@code LocaleResolver} bean -- this has zero effect on any other part of the app.
 */
@Component
public class ChatMessageSearchLocaleResolver {

    public ChatSearchLocale resolve(String acceptLanguageHeaderValue) {
        if (acceptLanguageHeaderValue == null || acceptLanguageHeaderValue.isBlank()) {
            return ChatSearchLocale.EN;
        }

        try {
            List<Locale.LanguageRange> ranges =
                    Locale.LanguageRange.parse(acceptLanguageHeaderValue);

            if (ranges.isEmpty()) {
                return ChatSearchLocale.EN;
            }

            String highestPriorityRange = ranges.get(0).getRange();
            String primaryTag = highestPriorityRange.split("-")[0];

            return "pt".equalsIgnoreCase(primaryTag) ? ChatSearchLocale.PT : ChatSearchLocale.EN;
        } catch (IllegalArgumentException e) {
            return ChatSearchLocale.EN;
        }
    }
}
