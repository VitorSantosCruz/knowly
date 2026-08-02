package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.ContactType;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryAddressDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit-style coverage of {@link StaffService#createStaffUser}'s REQ-2/REQ-3/REQ-4/REQ-5 role
 * selection (see specify/features/user-role-selection-at-creation/SPEC.md and PLAN.md's testing
 * strategy).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class StaffServiceTest {

    private static MandatoryProfileFieldsDto mandatoryProfile() {
        return new MandatoryProfileFieldsDto(
                "Test User",
                "52998224725",
                "BR",
                new MandatoryAddressDto(
                        "Rua Um, 100", "Centro", "Sao Paulo", "SP", "01000-000", "BR"),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    @Autowired private UserRepository userRepository;
    @Autowired private StaffService staffService;
    @Autowired private TenantContext tenantContext;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        tenantContext.clear();
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        user = userRepository.saveAndFlush(user);
        tenantContext.setStaffAdmin(true);
        return user;
    }

    private User limitedStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        user = userRepository.saveAndFlush(user);
        tenantContext.setStaffAdmin(false);
        return user;
    }

    // REQ-2: a STAFF_ADMIN caller can create a new STAFF_ADMIN.

    @Test
    void createStaffUserWithRoleStaffAdminSucceedsForAStaffAdminCaller() {
        staffAdmin("caller-staffadmin@example.com");
        authenticateAs("caller-staffadmin@example.com");

        User created =
                staffService.createStaffUser(
                        "new-staffadmin@example.com", GlobalRole.STAFF_ADMIN, mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);
    }

    // REQ-3: a STAFF caller (with or without STAFF_USER_CREATE) requesting STAFF_ADMIN is
    // rejected, no user created — permission grants never substitute for caller identity.

    @Test
    void createStaffUserWithRoleStaffAdminRejectsAStaffCaller() {
        limitedStaff("caller-staff@example.com");
        authenticateAs("caller-staff@example.com");

        assertThatThrownBy(
                        () ->
                                staffService.createStaffUser(
                                        "rejected-staffadmin@example.com",
                                        GlobalRole.STAFF_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rejected-staffadmin@example.com"))
                .isEmpty();
    }

    @Test
    void createStaffUserWithRoleStaffAdminRejectsAStaffCallerEvenWithStaffUserCreateGranted() {
        User staff = limitedStaff("caller-staff-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.STAFF_USER_CREATE));
        authenticateAs("caller-staff-granted@example.com");

        assertThatThrownBy(
                        () ->
                                staffService.createStaffUser(
                                        "rejected-staffadmin-granted@example.com",
                                        GlobalRole.STAFF_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rejected-staffadmin-granted@example.com"))
                .isEmpty();
    }

    // REQ-4: role=STAFF or omitted (null) behaves exactly as today.

    @Test
    void createStaffUserWithRoleStaffCreatesAStaffRow() {
        staffAdmin("caller-default@example.com");
        authenticateAs("caller-default@example.com");

        User created =
                staffService.createStaffUser(
                        "new-staff@example.com", GlobalRole.STAFF, mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF);
    }

    @Test
    void createStaffUserWithNullRoleDefaultsToStaff() {
        staffAdmin("caller-null-role@example.com");
        authenticateAs("caller-null-role@example.com");

        User created =
                staffService.createStaffUser(
                        "new-staff-null-role@example.com", null, mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF);
    }

    // REQ-5: no floor/ceiling check applies to creating a STAFF_ADMIN — succeeds whether zero or
    // many STAFF_ADMINs already exist.

    @Test
    void createStaffUserWithRoleStaffAdminSucceedsWithManyExistingStaffAdmins() {
        staffAdmin("caller-many-1@example.com");
        staffAdmin("existing-staffadmin-1@example.com");
        staffAdmin("existing-staffadmin-2@example.com");
        authenticateAs("caller-many-1@example.com");

        User created =
                staffService.createStaffUser(
                        "another-staffadmin@example.com",
                        GlobalRole.STAFF_ADMIN,
                        mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);
    }
}
