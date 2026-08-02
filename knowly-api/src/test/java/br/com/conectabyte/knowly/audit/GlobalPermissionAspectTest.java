package br.com.conectabyte.knowly.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * permission-granularity-model REQ-2/REQ-5: {@link GlobalPermissionAspect} enforces the same
 * view-dependency rule as {@link PermissionAspect}, for global-scope permissions.
 */
@Import({TestcontainersConfiguration.class, GlobalPermissionAspectTest.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class GlobalPermissionAspectTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private ProtectedService protectedService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private User newStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        user = userRepository.saveAndFlush(user);
        authenticateAs(email);

        return user;
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private void grant(User user, GlobalPermission permission) {
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, permission));
    }

    @Test
    void deleteWithViewPermissionProceeds() {
        User staff = newStaff("global-delete-with-view@example.com");
        grant(staff, GlobalPermission.TENANT_MEMBER_DELETE);
        grant(staff, GlobalPermission.TENANT_MEMBER_VIEW);

        assertThat(protectedService.deleteMember()).isEqualTo("deleted");
    }

    @Test
    void deleteWithoutViewPermissionIsDenied() {
        User staff = newStaff("global-delete-without-view@example.com");
        grant(staff, GlobalPermission.TENANT_MEMBER_DELETE);

        assertThatThrownBy(protectedService::deleteMember)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void viewAloneNeverGrantsDelete() {
        User staff = newStaff("global-view-alone@example.com");
        grant(staff, GlobalPermission.TENANT_MEMBER_VIEW);

        assertThatThrownBy(protectedService::deleteMember)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void createAloneIsUnaffectedByTheViewDependency() {
        User staff = newStaff("global-create-alone@example.com");
        grant(staff, GlobalPermission.TENANT_MEMBER_CREATE);

        assertThat(protectedService.createMember()).isEqualTo("created");
    }

    static class ProtectedService {
        @RequiresGlobalPermission(GlobalPermission.TENANT_MEMBER_DELETE)
        String deleteMember() {
            return "deleted";
        }

        @RequiresGlobalPermission(GlobalPermission.TENANT_MEMBER_CREATE)
        String createMember() {
            return "created";
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ProtectedService protectedService() {
            return new ProtectedService();
        }
    }
}
