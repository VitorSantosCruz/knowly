package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.AddressDto;
import br.com.conectabyte.knowly.identity.dto.ContactChangeDto;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryAddressDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.UserProfileDto;
import br.com.conectabyte.knowly.identity.exception.InvalidAvatarFileException;
import br.com.conectabyte.knowly.identity.exception.InvalidCpfException;
import br.com.conectabyte.knowly.identity.exception.ProfileAlreadyCompleteException;
import br.com.conectabyte.knowly.identity.exception.TaxIdAlreadyExistsException;
import br.com.conectabyte.knowly.identity.exception.UserNotFoundException;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * View/direct-edit of a {@code User}'s personal-data fields, now split across {@link UserProfile}/
 * {@link Address}/{@link Contact} (REQ-7..14), per
 * specify/features/identity-profile-model-v2/PLAN.md. Deliberately not {@code @Transactional} on
 * most methods here, mirroring {@code NotificationService}'s existing precedent: the admin-bypass/
 * shared-tenant-permission checks below must see every one of the caller's/target's memberships
 * across every tenant, not just whichever tenant happens to be the caller's currently-active one --
 * {@code TenantFilterAspect} only ever enables {@code TenantFilter} around
 * {@code @Transactional}-annotated methods, so staying outside that keeps these cross-tenant reads
 * unfiltered.
 *
 * <p>REQ-11 is a hard, unconditional self-exclusion: nobody -- not {@code STAFF_ADMIN}, not {@code
 * MEMBER_ADMIN} of their own tenant -- may directly edit their own record via {@link #directEdit};
 * only {@link #updateOwnAvatar} is self-editable, with no permission check beyond "is this the
 * caller's own row".
 */
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;
    private final ContactRepository contactRepository;
    private final ContactService contactService;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PermissionService permissionService;
    private final GlobalPermissionService globalPermissionService;
    private final BlindIndexService blindIndexService;
    private final AvatarStorageService avatarStorageService;
    private final AvatarProperties avatarProperties;
    private final ProfileCompletenessService profileCompletenessService;

    public UserProfileService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            AddressRepository addressRepository,
            ContactRepository contactRepository,
            ContactService contactService,
            TenantMembershipRepository tenantMembershipRepository,
            PermissionService permissionService,
            GlobalPermissionService globalPermissionService,
            BlindIndexService blindIndexService,
            AvatarStorageService avatarStorageService,
            AvatarProperties avatarProperties,
            ProfileCompletenessService profileCompletenessService) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.addressRepository = addressRepository;
        this.contactRepository = contactRepository;
        this.contactService = contactService;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.permissionService = permissionService;
        this.globalPermissionService = globalPermissionService;
        this.blindIndexService = blindIndexService;
        this.avatarStorageService = avatarStorageService;
        this.avatarProperties = avatarProperties;
        this.profileCompletenessService = profileCompletenessService;
    }

    /** REQ-7: the caller's own full profile detail, always allowed. */
    public UserProfileDto getOwnProfile(User caller) {
        return toDto(caller);
    }

    /** REQ-8/9: view another user's full profile detail. */
    @AuditLog(
            action = "identity.profile.view",
            resourceType = "User",
            resourceIdExpression = "#targetUserId")
    public UserProfileDto getProfile(User caller, Long targetUserId) {
        User target = requireUser(targetUserId);

        if (caller.getId().equals(target.getId())) {
            return toDto(target);
        }

        if (caller.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            return toDto(target);
        }

        if (isMemberAdminOfSharedTenant(caller, target)) {
            return toDto(target);
        }

        if (hasSharedTenantPermission(caller, target, Permission.PROFILE_VIEW)) {
            return toDto(target);
        }

        if (caller.getGlobalRole() == GlobalRole.STAFF
                && globalPermissionService.hasPermission(caller, GlobalPermission.PROFILE_VIEW)) {
            return toDto(target);
        }

        throw new PermissionDeniedException();
    }

    /** REQ-10: the only self-editable field, unconditional, no approval step. */
    @Transactional
    @AuditLog(action = "identity.profile.avatar.update", resourceType = "User")
    public UserProfileDto updateOwnAvatar(User caller, MultipartFile file) {
        validateAvatarFile(file);

        UserProfile profile = requireUserProfile(caller);
        String key = "users/" + caller.getId() + "/avatar";

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read uploaded avatar file", e);
        }

        avatarStorageService.upload(key, content, file.getContentType());
        profile.setAvatarUrl(avatarStorageService.presignedUrl(key).toString());
        userProfileRepository.save(profile);

        return toDto(caller);
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null
                || file.isEmpty()
                || !avatarProperties.allowedContentTypes().contains(file.getContentType())) {
            throw new InvalidAvatarFileException();
        }
        if (file.getSize() > avatarProperties.maxFileSize().toBytes()) {
            throw new InvalidAvatarFileException();
        }
    }

    /**
     * REQ-11/12/13: directly edit a user's non-avatar personal-data fields. Deliberately not
     * {@code @Transactional} here (same rationale as the class-level Javadoc) -- {@link
     * #hasDirectEditRight} must see every membership across every tenant; atomicity of the actual
     * write is instead owned by {@link #applyFields} itself, which is {@code @Transactional}.
     */
    @AuditLog(
            action = "identity.profile.edit",
            resourceType = "User",
            resourceIdExpression = "#targetUserId")
    public UserProfileDto directEdit(User caller, Long targetUserId, ProfileFieldsDto fields) {
        return directEdit(caller, targetUserId, fields, List.of());
    }

    /**
     * Same as {@link #directEdit(User, Long, ProfileFieldsDto)}, additionally applying any contact
     * add/update/remove the caller submitted -- a direct-edit caller (e.g. {@code
     * MEMBER_ADMIN}/{@code STAFF_ADMIN}) can change the target's contacts in the same call, the
     * same way {@code ProfileEditRequestService}'s approve path already does.
     */
    @AuditLog(
            action = "identity.profile.edit",
            resourceType = "User",
            resourceIdExpression = "#targetUserId")
    public UserProfileDto directEdit(
            User caller,
            Long targetUserId,
            ProfileFieldsDto fields,
            List<ContactChangeDto> contactChanges) {
        User target = requireUser(targetUserId);

        if (!hasDirectEditRight(caller, target)) {
            throw new PermissionDeniedException();
        }

        applyFields(target, fields, contactChanges);

        return toDto(target);
    }

    /**
     * REQ-11/12/13 decision tree, shared by {@link #directEdit} and (against the original requester
     * as target) {@code ProfileEditRequestService#approveEditRequest}/{@code #rejectEditRequest}
     * (REQ-19). Package-private so {@code ProfileEditRequestService} (same package) can re-run the
     * same check at approval time. REQ-11: self is unconditionally excluded, regardless of which of
     * the four "may edit others" paths the caller holds.
     */
    boolean hasDirectEditRight(User caller, User target) {
        boolean isSelf = caller.getId().equals(target.getId());

        if (isSelf) {
            return false;
        }

        boolean allowed =
                caller.getGlobalRole() == GlobalRole.STAFF_ADMIN
                        || isMemberAdminOfSharedTenant(caller, target);

        if (!allowed) {
            allowed =
                    hasSharedTenantPermission(caller, target, Permission.PROFILE_EDIT)
                            || (caller.getGlobalRole() == GlobalRole.STAFF
                                    && globalPermissionService.hasPermission(
                                            caller, GlobalPermission.PROFILE_EDIT));
        }

        return allowed;
    }

    /**
     * The single choke point every direct-edit/approve call site routes through: plain fields are
     * set directly on {@link UserProfile}/{@link Address}, {@code taxId} is additionally routed
     * through {@link BlindIndexService} in the same call, and every {@code contactChanges} entry is
     * applied via {@link ContactService} -- all inside the same transactional boundary as the
     * caller (REQ-17's atomicity). Package-private so {@link ProfileEditRequestService} (same
     * package) reuses it for approvals rather than duplicating the write.
     */
    @Transactional
    void applyFields(User target, ProfileFieldsDto fields, List<ContactChangeDto> contactChanges) {
        UserProfile profile = requireUserProfile(target);

        if (fields != null) {
            if (fields.fullName() != null) {
                profile.setFullName(fields.fullName());
            }
            if (fields.countryCode() != null) {
                profile.setCountryCode(fields.countryCode());
            }
            if (fields.taxId() != null) {
                String normalizedTaxId = IdentityFieldNormalizer.stripFormatting(fields.taxId());
                requireValidTaxId(normalizedTaxId, profile.getCountryCode());
                profile.setTaxId(normalizedTaxId);
                profile.setTaxIdBlindIndex(blindIndexService.hmac(normalizedTaxId));
            }
            userProfileRepository.save(profile);

            if (fields.address() != null) {
                applyAddress(target, fields.address());
            }
        }

        if (contactChanges != null) {
            for (ContactChangeDto change : contactChanges) {
                applyContactChange(target, change);
            }
        }
    }

    /**
     * REQ-6: the bootstrap account's one-time, no-approval self-completion. Applies only to the
     * authenticated caller's own record (no {@code id} path variable at the controller level,
     * removing any possibility of using this exception to complete someone else's profile); rejects
     * outright if the caller's profile is already complete (SPEC's Decision 3 -- this never reopens
     * identity-profile-model-v2's existing self-request/approval requirement for changes to an
     * already-set field).
     */
    @Transactional
    @AuditLog(action = "identity.profile.complete", resourceType = "User")
    public UserProfileDto completeOwnProfile(User caller, MandatoryProfileFieldsDto fields) {
        if (profileCompletenessService.isComplete(caller)) {
            throw new ProfileAlreadyCompleteException();
        }

        applyMandatoryProfile(caller, fields);

        return toDto(caller);
    }

    /**
     * REQ-7/REQ-8/REQ-6: writes the full mandatory profile field set (per
     * specify/features/mandatory-complete-profile/PLAN.md's "one shared mandatory profile fields
     * DTO shape" decision) into {@code target}'s {@link UserProfile}/{@link Address}/{@link
     * Contact} rows, atomically. Reused by staff creation, {@code addMember}, and the bootstrap
     * completion endpoint -- no permission check here, each call site is responsible for its own
     * (creation-time precondition for the first two, "is this the caller's own row" for the third).
     */
    @Transactional
    public void applyMandatoryProfile(User target, MandatoryProfileFieldsDto fields) {
        UserProfile profile = requireUserProfile(target);

        String normalizedTaxId = IdentityFieldNormalizer.stripFormatting(fields.taxId());
        requireValidTaxId(normalizedTaxId, fields.countryCode());

        String taxIdBlindIndex = blindIndexService.hmac(normalizedTaxId);
        // Proactive check, not "insert and catch DataIntegrityViolationException": this method
        // runs inside its own @Transactional/@AuditLog boundary (same reasoning as
        // TenantService#createTenant's taxId/adminEmail checks), so a failed insert here would
        // leave the persistence context in a state Hibernate can't safely keep flushing against
        // within the same still-open transaction. Previously this reached the DB's own unique
        // index on tax_id_blind_index unchecked, surfacing as an unhandled 500 with a leaked SQL
        // stack trace instead of a clean conflict response.
        if (userProfileRepository.existsByTaxIdBlindIndexAndUserIdNot(
                taxIdBlindIndex, target.getId())) {
            throw new TaxIdAlreadyExistsException();
        }

        profile.setFullName(fields.fullName());
        profile.setCountryCode(fields.countryCode());
        profile.setTaxId(normalizedTaxId);
        profile.setTaxIdBlindIndex(taxIdBlindIndex);
        userProfileRepository.save(profile);

        MandatoryAddressDto addressDto = fields.address();
        Address address =
                addressRepository.findById(target.getId()).orElseGet(() -> new Address(target));
        address.setAddressLine1(addressDto.addressLine1());
        address.setAddressLine2(addressDto.addressLine2());
        address.setCity(addressDto.city());
        address.setStateRegion(addressDto.stateRegion());
        address.setPostalCode(IdentityFieldNormalizer.stripFormatting(addressDto.postalCode()));
        address.setCountryCode(
                addressDto.countryCode() != null
                        ? addressDto.countryCode()
                        : profile.getCountryCode());
        addressRepository.save(address);

        for (ContactDto contact : fields.contacts()) {
            contactService.addContact(
                    target,
                    contact.type(),
                    contact.value(),
                    contact.label(),
                    Boolean.TRUE.equals(contact.isPrimary()));
        }
    }

    /**
     * REQ-4a: throws {@link InvalidCpfException} if {@code normalizedTaxId} is present, {@code
     * countryCode} is {@code "BR"}, and it fails the mod-11 checksum (2026-08-02 country-agnostic
     * amendment: the checksum is now conditional on {@code countryCode}, not unconditional -- every
     * other country's {@code taxId} receives normalization only). A {@code null}/absent value is a
     * presence question, not this method's concern, so it's silently skipped.
     */
    void requireValidTaxId(String normalizedTaxId, String countryCode) {
        if (normalizedTaxId != null
                && "BR".equals(countryCode)
                && !CpfChecksumValidator.isValid(normalizedTaxId)) {
            throw new InvalidCpfException();
        }
    }

    private void applyAddress(User target, AddressDto addressDto) {
        UserProfile profile = requireUserProfile(target);
        Address address =
                addressRepository.findById(target.getId()).orElseGet(() -> new Address(target));

        if (addressDto.addressLine1() != null) {
            address.setAddressLine1(addressDto.addressLine1());
        }
        if (addressDto.addressLine2() != null) {
            address.setAddressLine2(addressDto.addressLine2());
        }
        if (addressDto.city() != null) {
            address.setCity(addressDto.city());
        }
        if (addressDto.stateRegion() != null) {
            address.setStateRegion(addressDto.stateRegion());
        }
        if (addressDto.postalCode() != null) {
            address.setPostalCode(IdentityFieldNormalizer.stripFormatting(addressDto.postalCode()));
        }
        address.setCountryCode(
                addressDto.countryCode() != null
                        ? addressDto.countryCode()
                        : (address.getCountryCode() != null
                                ? address.getCountryCode()
                                : profile.getCountryCode()));

        addressRepository.save(address);
    }

    private void applyContactChange(User target, ContactChangeDto change) {
        switch (change.action()) {
            case ADD ->
                    contactService.addContact(
                            target,
                            change.type(),
                            change.value(),
                            change.label(),
                            Boolean.TRUE.equals(change.isPrimary()));
            case UPDATE -> {
                Contact contact = requireOwnContact(target, change.contactId());
                contactService.updateContact(
                        contact, change.type(), change.value(), change.label(), change.isPrimary());
            }
            case REMOVE ->
                    contactService.removeContact(requireOwnContact(target, change.contactId()));
        }
    }

    private Contact requireOwnContact(User target, Long contactId) {
        Contact contact =
                contactRepository.findById(contactId).orElseThrow(UserNotFoundException::new);
        if (!contact.getUser().getId().equals(target.getId())) {
            throw new PermissionDeniedException();
        }
        return contact;
    }

    /** REQ-11: is the caller MEMBER_ADMIN of any tenant the target is also an active member of. */
    boolean isMemberAdminOfSharedTenant(User caller, User target) {
        Set<Long> callerAdminTenantIds =
                tenantMembershipRepository.findByUserAndActiveTrue(caller).stream()
                        .filter(membership -> membership.getRole() == MembershipRole.MEMBER_ADMIN)
                        .map(membership -> membership.getTenant().getId())
                        .collect(Collectors.toSet());

        if (callerAdminTenantIds.isEmpty()) {
            return false;
        }

        return tenantMembershipRepository.findByUserAndActiveTrue(target).stream()
                .map(membership -> membership.getTenant().getId())
                .anyMatch(callerAdminTenantIds::contains);
    }

    /** REQ-9/13: does the caller hold {@code permission} in a tenant the target also belongs to. */
    boolean hasSharedTenantPermission(User caller, User target, Permission permission) {
        Set<Long> targetTenantIds =
                tenantMembershipRepository.findByUserAndActiveTrue(target).stream()
                        .map(membership -> membership.getTenant().getId())
                        .collect(Collectors.toSet());

        return tenantMembershipRepository.findByUserAndActiveTrue(caller).stream()
                .filter(membership -> targetTenantIds.contains(membership.getTenant().getId()))
                .anyMatch(membership -> permissionService.hasPermission(membership, permission));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(UserNotFoundException::new);
    }

    UserProfile requireUserProfile(User user) {
        return userProfileRepository
                .findById(user.getId())
                .orElseGet(() -> userProfileRepository.save(new UserProfile(user)));
    }

    private UserProfileDto toDto(User user) {
        UserProfile profile = requireUserProfile(user);
        Address address = addressRepository.findById(user.getId()).orElse(null);
        List<ContactDto> contacts =
                contactRepository.findByUserAndDeletedAtIsNull(user).stream()
                        .map(
                                contact ->
                                        new ContactDto(
                                                contact.getId(),
                                                contact.getType(),
                                                contact.getValue(),
                                                contact.getLabel(),
                                                contact.isPrimary()))
                        .toList();

        AddressDto addressDto =
                address == null
                        ? null
                        : new AddressDto(
                                address.getAddressLine1(),
                                address.getAddressLine2(),
                                address.getCity(),
                                address.getStateRegion(),
                                address.getPostalCode(),
                                address.getCountryCode());

        ProfileFieldsDto fields =
                new ProfileFieldsDto(
                        profile.getFullName(),
                        profile.getTaxId(),
                        profile.getCountryCode(),
                        addressDto,
                        contacts);

        return UserProfileDto.of(user.getId(), user.getEmail(), fields, profile.getAvatarUrl());
    }
}
