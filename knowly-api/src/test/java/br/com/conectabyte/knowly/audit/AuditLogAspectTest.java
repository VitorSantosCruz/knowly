package br.com.conectabyte.knowly.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AccountLockedException;
import br.com.conectabyte.knowly.auth.exception.InvalidCredentialsException;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Import({TestcontainersConfiguration.class, AuditLogAspectTest.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class AuditLogAspectTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private AuditedService auditedService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private User authenticateAs(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);

        return user;
    }

    @Test
    void writesAnAuditEventOnSuccess() {
        User user = authenticateAs("success@example.com");
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Acme"));
        tenantContext.setActiveTenantId(tenant.getId());

        auditedService.doSomething("42");

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getAction()).isEqualTo("test.do-something");
        assertThat(event.getResourceType()).isEqualTo("TestResource");
        assertThat(event.getResourceId()).isEqualTo("42");
        assertThat(event.getTenantId()).isEqualTo(tenant.getId());
    }

    @Test
    void writesAnAuditEventWithErrorOutcomeWhenTheMethodThrows() {
        User user = authenticateAs("error@example.com");

        assertThatThrownBy(() -> auditedService.doSomethingThatFails())
                .isInstanceOf(IllegalStateException.class);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.ERROR);
    }

    @Test
    void writesAnAuditEventWithFailureOutcomeWhenTheMethodThrowsInvalidCredentials() {
        User user = authenticateAs("invalid-credentials@example.com");

        assertThatThrownBy(() -> auditedService.doSomethingWithInvalidCredentials())
                .isInstanceOf(InvalidCredentialsException.class);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
    }

    @Test
    void writesAnAuditEventWithLockedOutOutcomeWhenTheMethodThrowsAccountLocked() {
        User user = authenticateAs("account-locked@example.com");

        assertThatThrownBy(() -> auditedService.doSomethingWithAccountLocked())
                .isInstanceOf(AccountLockedException.class);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.LOCKED_OUT);
    }

    @Test
    void capturesTheMaskedSourceIpIntoMetadataWhenCaptureSourceIpIsTrueAndARequestContextExists() {
        User user = authenticateAs("source-ip@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditedService.doSomethingWithSourceIpCapture("1");

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMetadata()).contains("203.0.113.0");
        assertThat(events.get(0).getMetadata()).doesNotContain("203.0.113.7");
    }

    @Test
    void doesNotThrowWhenCaptureSourceIpIsTrueButNoRequestContextIsAvailable() {
        User user = authenticateAs("no-request-context@example.com");
        RequestContextHolder.resetRequestAttributes();

        auditedService.doSomethingWithSourceIpCapture("1");

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMetadata()).isNull();
    }

    @Test
    void neverPopulatesMetadataWhenCaptureSourceIpIsFalseEvenWithARequestContext() {
        User user = authenticateAs("default-no-capture@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditedService.doSomething("1");

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMetadata()).isNull();
    }

    @Test
    void writesAnAuditEventForAReadOnlyMethodWithNoStateChange() {
        User user = authenticateAs("reader@example.com");

        auditedService.readSomething();

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(user.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAction()).isEqualTo("test.read-something");
        assertThat(events.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    static class AuditedService {
        @AuditLog(
                action = "test.do-something",
                resourceType = "TestResource",
                resourceIdExpression = "#id")
        String doSomething(String id) {
            return "ok:" + id;
        }

        @AuditLog(action = "test.do-something-that-fails")
        void doSomethingThatFails() {
            throw new IllegalStateException("boom");
        }

        @AuditLog(action = "test.do-something-with-invalid-credentials")
        void doSomethingWithInvalidCredentials() {
            throw new InvalidCredentialsException();
        }

        @AuditLog(action = "test.do-something-with-account-locked")
        void doSomethingWithAccountLocked() {
            throw new AccountLockedException();
        }

        @AuditLog(action = "test.read-something")
        String readSomething() {
            return "read-only, no state change";
        }

        @AuditLog(action = "test.do-something-with-source-ip-capture", captureSourceIp = true)
        String doSomethingWithSourceIpCapture(String id) {
            return "ok:" + id;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        AuditedService auditedService() {
            return new AuditedService();
        }
    }
}
