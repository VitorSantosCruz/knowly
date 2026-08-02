package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.AddressDto;
import br.com.conectabyte.knowly.identity.dto.ContactChangeDto;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryAddressDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.exception.InvalidCpfException;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers {@link UserProfileService}'s full REQ-7..14 decision matrix, per
 * specify/features/identity-profile-model-v2/SPEC.md/PLAN.md -- REQ-11 is an unconditional
 * self-exclusion, superseding identity-profile-model's old admin-self-edit allowance.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserProfileServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private UserProfileService userProfileService;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private BlindIndexService blindIndexService;

    private static final ProfileFieldsDto FIELDS =
            new ProfileFieldsDto("Jane Doe", null, null, null, null);

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private TenantMembership membershipOf(User user, Tenant tenant, MembershipRole role) {
        return tenantMembershipRepository.saveAndFlush(new TenantMembership(user, tenant, role));
    }

    private void grantTenantPermission(TenantMembership membership, Permission permission) {
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, permission));
    }

    private void grantGlobalPermission(User user, GlobalPermission permission) {
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, permission));
    }

    // ---- view (REQ-7/8/9) ----

    @Test
    void getOwnProfileAlwaysSucceeds() {
        User caller = user("own-profile@example.com");

        assertThat(userProfileService.getOwnProfile(caller).userId()).isEqualTo(caller.getId());
    }

    @Test
    void viewingAnotherUsersProfileWithNoApplicableRightIsRejected() {
        User caller = user("view-norights@example.com");
        User target = user("view-target-norights@example.com");

        assertThatThrownBy(() -> userProfileService.getProfile(caller, target.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aTenantScopedProfileViewHolderCanViewAnyMemberOfThatTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("View Co"));
        User caller = user("view-holder@example.com");
        User target = user("view-target@example.com");
        TenantMembership callerMembership = membershipOf(caller, tenant, MembershipRole.MEMBER);
        membershipOf(target, tenant, MembershipRole.MEMBER);
        grantTenantPermission(callerMembership, Permission.PROFILE_VIEW);

        assertThat(userProfileService.getProfile(caller, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    @Test
    void aGlobalScopedProfileViewHolderCanViewAnyUser() {
        User caller = user("global-view-holder@example.com");
        caller.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(caller);
        grantGlobalPermission(caller, GlobalPermission.PROFILE_VIEW);
        User target = user("global-view-target@example.com");

        assertThat(userProfileService.getProfile(caller, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    @Test
    void aMemberAdminCanViewAnyMemberOfTheirTenantWithNoSeparateGrant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Admin View Co"));
        User admin = user("admin-view@example.com");
        User target = user("admin-view-target@example.com");
        membershipOf(admin, tenant, MembershipRole.MEMBER_ADMIN);
        membershipOf(target, tenant, MembershipRole.MEMBER);

        assertThat(userProfileService.getProfile(admin, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    @Test
    void aStaffAdminCanViewAnyUserWithNoSeparateGrant() {
        User staffAdmin = user("staff-admin-view@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User target = user("staff-admin-view-target@example.com");

        assertThat(userProfileService.getProfile(staffAdmin, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    // ---- direct edit (REQ-11/12/13): self is ALWAYS excluded now ----

    @Test
    void aMemberAdminCanEditOthersWithinTheirTenantButNeverThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Admin Edit Co"));
        User admin = user("admin-edit@example.com");
        User other = user("admin-edit-other@example.com");
        membershipOf(admin, tenant, MembershipRole.MEMBER_ADMIN);
        membershipOf(other, tenant, MembershipRole.MEMBER);

        assertThat(userProfileService.directEdit(admin, other.getId(), FIELDS).fields().fullName())
                .isEqualTo("Jane Doe");
        assertThatThrownBy(() -> userProfileService.directEdit(admin, admin.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aStaffAdminCanEditAnyOtherUserButNeverThemselves() {
        User staffAdmin = user("staff-admin-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User other = user("staff-admin-edit-other@example.com");

        assertThat(
                        userProfileService
                                .directEdit(staffAdmin, other.getId(), FIELDS)
                                .fields()
                                .fullName())
                .isEqualTo("Jane Doe");
        assertThatThrownBy(
                        () -> userProfileService.directEdit(staffAdmin, staffAdmin.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aTenantScopedProfileEditHolderCanEditAnOtherMemberButNotThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Co"));
        User holder = user("edit-holder@example.com");
        User other = user("edit-holder-other@example.com");
        TenantMembership holderMembership = membershipOf(holder, tenant, MembershipRole.MEMBER);
        membershipOf(other, tenant, MembershipRole.MEMBER);
        grantTenantPermission(holderMembership, Permission.PROFILE_EDIT);

        assertThat(userProfileService.directEdit(holder, other.getId(), FIELDS).fields().fullName())
                .isEqualTo("Jane Doe");
        assertThatThrownBy(() -> userProfileService.directEdit(holder, holder.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aGlobalScopedProfileEditHolderCanEditAnOtherUserButNotThemselves() {
        User holder = user("global-edit-holder@example.com");
        holder.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(holder);
        grantGlobalPermission(holder, GlobalPermission.PROFILE_EDIT);
        User other = user("global-edit-holder-other@example.com");

        assertThat(userProfileService.directEdit(holder, other.getId(), FIELDS).fields().fullName())
                .isEqualTo("Jane Doe");
        assertThatThrownBy(() -> userProfileService.directEdit(holder, holder.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aCallerWithNoApplicableRightIsRejectedEntirely() {
        User caller = user("edit-norights@example.com");
        User other = user("edit-norights-other@example.com");

        assertThatThrownBy(() -> userProfileService.directEdit(caller, other.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void directEditAlsoAppliesContactChangesForTheTarget() {
        User staffAdmin = user("staff-admin-edit-contacts@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User other = user("staff-admin-edit-contacts-other@example.com");
        List<ContactChangeDto> contactChanges =
                List.of(
                        new ContactChangeDto(
                                ContactChangeAction.ADD,
                                null,
                                ContactType.PHONE,
                                "+5511988887777",
                                "Mobile",
                                true));

        var result =
                userProfileService.directEdit(staffAdmin, other.getId(), FIELDS, contactChanges);

        assertThat(result.fields().contacts()).hasSize(1);
        assertThat(result.fields().contacts().get(0).value()).isEqualTo("+5511988887777");
    }

    // ---- avatar (REQ-10): always self-editable, no approval, no permission check ----

    @Test
    void updateOwnAvatarAlwaysSucceedsRegardlessOfRoleOrPermission() {
        User user = user("avatar-self@example.com");
        MockMultipartFile file =
                new MockMultipartFile("file", "avatar.png", "image/png", "fake-bytes".getBytes());

        assertThat(userProfileService.updateOwnAvatar(user, file).avatarUrl()).isNotBlank();
    }

    // ---- REQ-4a/country-agnostic amendment: normalization + BR-conditional tax-id checksum ----

    @Test
    void directEditNormalizesAMaskedTaxIdAndPostalCode() {
        User staffAdmin = user("normalize-direct-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User masked = user("normalize-direct-edit-masked@example.com");
        userProfileService.directEdit(
                staffAdmin,
                masked.getId(),
                new ProfileFieldsDto("Jane Doe", null, "BR", null, null));

        ProfileFieldsDto maskedFields =
                new ProfileFieldsDto(
                        "Jane Doe",
                        "529.982.247-25",
                        "BR",
                        new AddressDto(
                                "Rua Um, 100", "Centro", "Sao Paulo", "SP", "12345-678", "BR"),
                        null);

        userProfileService.directEdit(staffAdmin, masked.getId(), maskedFields);

        UserProfile maskedProfile = userProfileRepository.findById(masked.getId()).orElseThrow();
        assertThat(maskedProfile.getTaxId()).isEqualTo("52998224725");
        assertThat(maskedProfile.getTaxIdBlindIndex())
                .isEqualTo(blindIndexService.hmac("52998224725"));
        assertThat(addressRepository.findById(masked.getId()).orElseThrow().getPostalCode())
                .isEqualTo("12345678");
    }

    @Test
    void aMaskedAndAnAlreadyPlainTaxIdProduceTheIdenticalNormalizedResultAndBlindIndex() {
        User staffAdmin = user("normalize-equiv-direct-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User masked = user("normalize-equiv-masked@example.com");
        User plain = user("normalize-equiv-plain@example.com");
        AddressDto address =
                new AddressDto("Rua Um, 100", "Centro", "Sao Paulo", "SP", "12345678", "BR");

        userProfileService.directEdit(
                staffAdmin,
                masked.getId(),
                new ProfileFieldsDto("Jane Doe", "529.982.247-25", "BR", address, null));
        userProfileService.directEdit(
                staffAdmin,
                plain.getId(),
                new ProfileFieldsDto("Jane Doe", "11144477735", "BR", address, null));

        UserProfile maskedProfile = userProfileRepository.findById(masked.getId()).orElseThrow();
        UserProfile plainProfile = userProfileRepository.findById(plain.getId()).orElseThrow();
        assertThat(maskedProfile.getTaxId()).isEqualTo("52998224725");
        assertThat(plainProfile.getTaxId()).isEqualTo("11144477735");
    }

    @Test
    void directEditRejectsAChecksumInvalidTaxIdWhenCountryCodeIsBrBeforeAnyPersistence() {
        User staffAdmin = user("invalid-taxid-direct-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User target = user("invalid-taxid-direct-edit-target@example.com");
        userProfileService.directEdit(
                staffAdmin, target.getId(), new ProfileFieldsDto(null, null, "BR", null, null));
        ProfileFieldsDto invalidFields =
                new ProfileFieldsDto("Jane Doe", "111.111.111-11", "BR", null, null);

        assertThatThrownBy(
                        () ->
                                userProfileService.directEdit(
                                        staffAdmin, target.getId(), invalidFields))
                .isInstanceOf(InvalidCpfException.class);

        assertThat(
                        userProfileRepository
                                .findById(target.getId())
                                .map(UserProfile::getTaxId)
                                .orElse(null))
                .isNull();
    }

    @Test
    void directEditAcceptsAnyShapeTaxIdWhenCountryCodeIsNotBr() {
        User staffAdmin = user("nonbr-taxid-direct-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User target = user("nonbr-taxid-direct-edit-target@example.com");
        userProfileService.directEdit(
                staffAdmin, target.getId(), new ProfileFieldsDto(null, null, "US", null, null));

        userProfileService.directEdit(
                staffAdmin,
                target.getId(),
                new ProfileFieldsDto("Jane Doe", "111-11-1111", "US", null, null));

        assertThat(userProfileRepository.findById(target.getId()).orElseThrow().getTaxId())
                .isEqualTo("111111111");
    }

    // ---- REQ-4a/country-agnostic amendment: applyMandatoryProfile (bootstrap completion) ----

    private static MandatoryProfileFieldsDto mandatoryFieldsWithTaxId(String taxId) {
        return new MandatoryProfileFieldsDto(
                "Jane Doe",
                taxId,
                "BR",
                new MandatoryAddressDto(
                        "Rua Um, 100", "Centro", "Sao Paulo", "SP", "12345-678", "BR"),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    @Test
    void completeOwnProfileNormalizesAMaskedTaxIdAndPostalCodeBeforePersistence() {
        User caller = user("normalize-mandatory@example.com");

        userProfileService.completeOwnProfile(caller, mandatoryFieldsWithTaxId("529.982.247-25"));

        UserProfile profile = userProfileRepository.findById(caller.getId()).orElseThrow();
        assertThat(profile.getTaxId()).isEqualTo("52998224725");
        assertThat(addressRepository.findById(caller.getId()).orElseThrow().getPostalCode())
                .isEqualTo("12345678");
    }

    @Test
    void completeOwnProfileRejectsAChecksumInvalidTaxIdBeforeAnyRowIsWritten() {
        User caller = user("invalid-taxid-mandatory@example.com");

        assertThatThrownBy(
                        () ->
                                userProfileService.completeOwnProfile(
                                        caller, mandatoryFieldsWithTaxId("111.111.111-11")))
                .isInstanceOf(InvalidCpfException.class);

        assertThat(addressRepository.findById(caller.getId())).isEmpty();
    }
}
