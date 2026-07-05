package br.com.conectabyte.knowly.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class JpaAuditingConfigTest {

    private final JpaAuditingConfig config = new JpaAuditingConfig();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSystemWhenNoAuthenticatedUser() {
        AuditorAware<String> auditorAware = config.auditorAware();

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void returnsAuthenticatedUsernameWhenPresent() {
        var authentication =
                new UsernamePasswordAuthenticationToken("user@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AuditorAware<String> auditorAware = config.auditorAware();

        assertThat(auditorAware.getCurrentAuditor()).contains("user@example.com");
    }
}
