package br.com.conectabyte.knowly.deletion;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * REQ-31: resolves the caller's active locale from the raw {@code Accept-Language} header value —
 * narrower than Spring's general {@code AcceptHeaderLocaleResolver}: only the highest-priority
 * range's primary language tag is checked against {@code "pt"}; anything else (including a missing,
 * empty, or unparseable header) resolves to {@link DeletionLocale#EN}. Not registered as a Spring
 * {@code LocaleResolver} bean — this has zero effect on any other part of the app.
 */
@Component
public class DeletionConfirmationLocaleResolver {

    public DeletionLocale resolve(String acceptLanguageHeaderValue) {
        if (acceptLanguageHeaderValue == null || acceptLanguageHeaderValue.isBlank()) {
            return DeletionLocale.EN;
        }

        try {
            List<Locale.LanguageRange> ranges =
                    Locale.LanguageRange.parse(acceptLanguageHeaderValue);

            if (ranges.isEmpty()) {
                return DeletionLocale.EN;
            }

            String highestPriorityRange = ranges.get(0).getRange();
            String primaryTag = highestPriorityRange.split("-")[0];

            return "pt".equalsIgnoreCase(primaryTag) ? DeletionLocale.PT_BR : DeletionLocale.EN;
        } catch (IllegalArgumentException e) {
            return DeletionLocale.EN;
        }
    }
}
