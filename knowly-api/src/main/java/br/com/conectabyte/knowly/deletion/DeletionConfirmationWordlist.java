package br.com.conectabyte.knowly.deletion;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the two static, in-repo, curated deletion-confirmation wordlists (REQ-2/REQ-31) once at
 * startup. A startup check fails fast if either list has fewer than {@link #MIN_ENTRIES} entries so
 * a curation mistake is caught in CI/deploy rather than silently producing weak tokens in
 * production.
 */
@Component
public class DeletionConfirmationWordlist {

    static final int MIN_ENTRIES = 512;

    private static final String EN_RESOURCE = "wordlists/deletion-confirmation-en.txt";
    private static final String PT_BR_RESOURCE = "wordlists/deletion-confirmation-pt-br.txt";

    private List<String> en;
    private List<String> ptBr;

    @PostConstruct
    void load() {
        en = loadList(EN_RESOURCE);
        ptBr = loadList(PT_BR_RESOURCE);
    }

    public List<String> forLocale(DeletionLocale locale) {
        return locale == DeletionLocale.PT_BR ? ptBr : en;
    }

    private List<String> loadList(String resourcePath) {
        List<String> words = new ArrayList<>();

        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource(resourcePath).getInputStream(),
                                StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (!trimmed.isEmpty()) {
                    words.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load wordlist: " + resourcePath, e);
        }

        if (words.size() < MIN_ENTRIES) {
            throw new IllegalStateException(
                    "Wordlist "
                            + resourcePath
                            + " has only "
                            + words.size()
                            + " entries, minimum required is "
                            + MIN_ENTRIES);
        }

        if (words.size() != words.stream().distinct().count()) {
            throw new IllegalStateException(
                    "Wordlist " + resourcePath + " contains duplicate entries");
        }

        return List.copyOf(words);
    }
}
