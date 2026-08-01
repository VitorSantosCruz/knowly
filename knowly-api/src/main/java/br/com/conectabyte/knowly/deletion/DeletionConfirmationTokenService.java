package br.com.conectabyte.knowly.deletion;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.AuthProperties;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Generic deletion confirmation token mechanism (REQ-1), reusable by any delete endpoint that opts
 * in. Storage is Redis, mirroring {@code LoginCodeService}'s existing one-time-secret convention —
 * see PLAN.md for the full rationale.
 */
@Service
public class DeletionConfirmationTokenService {

    private static final String KEY_PREFIX = "deletion-token:";

    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final DeletionConfirmationWordlist wordlist;
    private final DeletionConfirmationLocaleResolver localeResolver;
    private final AuditEventWriter auditEventWriter;
    private final TenantContext tenantContext;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public DeletionConfirmationTokenService(
            StringRedisTemplate redisTemplate,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            DeletionConfirmationWordlist wordlist,
            DeletionConfirmationLocaleResolver localeResolver,
            AuditEventWriter auditEventWriter,
            TenantContext tenantContext) {
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.wordlist = wordlist;
        this.localeResolver = localeResolver;
        this.auditEventWriter = auditEventWriter;
        this.tenantContext = tenantContext;
        this.dummyHash = passwordEncoder.encode("dummy-constant-for-timing-safety");
    }

    /**
     * REQ-2/REQ-12: draws two distinct words from the resolved locale's wordlist, persists them
     * hashed with the configured TTL (overwriting/invalidating any prior live token for this exact
     * key), audit-logs the generation, and returns the plaintext word.
     */
    public String generate(
            String resourceType, String resourceId, User actor, String acceptLanguageHeaderValue) {
        DeletionLocale locale = localeResolver.resolve(acceptLanguageHeaderValue);
        String word = drawTwoDistinctWords(locale);

        redisTemplate
                .opsForValue()
                .set(
                        key(resourceType, resourceId, actor),
                        passwordEncoder.encode(word),
                        properties.deletionConfirmationToken().ttl());

        writeAudit(
                "deletion_confirmation_token.generate",
                resourceType,
                resourceId,
                actor,
                AuditOutcome.SUCCESS);

        return word;
    }

    /**
     * REQ-6/REQ-7/REQ-8/REQ-9/REQ-10/REQ-11/REQ-32: single-use on the first attempt, regardless of
     * outcome. A missing key still compares against a dummy hash (timing safety).
     */
    public boolean validateAndConsume(
            String resourceType, String resourceId, User actor, String suppliedWord) {
        String redisKey = key(resourceType, resourceId, actor);
        String hash = redisTemplate.opsForValue().get(redisKey);
        boolean matches = passwordEncoder.matches(suppliedWord, hash != null ? hash : dummyHash);
        boolean success = hash != null && matches;

        if (hash != null) {
            redisTemplate.delete(redisKey);
        }

        writeAudit(
                "deletion_confirmation_token.validate",
                resourceType,
                resourceId,
                actor,
                success ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE);

        return success;
    }

    private String drawTwoDistinctWords(DeletionLocale locale) {
        List<String> words = wordlist.forLocale(locale);
        String first = words.get(random.nextInt(words.size()));
        String second;

        do {
            second = words.get(random.nextInt(words.size()));
        } while (second.equals(first));

        return first + "-" + second;
    }

    private String key(String resourceType, String resourceId, User actor) {
        return KEY_PREFIX + resourceType + ":" + resourceId + ":" + actor.getId();
    }

    private void writeAudit(
            String action,
            String resourceType,
            String resourceId,
            User actor,
            AuditOutcome outcome) {
        AuditEvent event =
                new AuditEvent(
                        actor.getId(),
                        tenantContext.getActiveTenantId().orElse(null),
                        action,
                        resourceType,
                        resourceId,
                        outcome);
        auditEventWriter.write(event);
    }
}
