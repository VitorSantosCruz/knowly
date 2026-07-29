package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.AddressDto;
import br.com.conectabyte.knowly.identity.dto.ContactChangeDto;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.UserProfileDto;
import br.com.conectabyte.knowly.identity.exception.InvalidAvatarFileException;
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
            AvatarProperties avatarProperties) {
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
        User target = requireUser(targetUserId);

        if (!hasDirectEditRight(caller, target)) {
            throw new PermissionDeniedException();
        }

        applyFields(target, fields, List.of());

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
     * set directly on {@link UserProfile}/{@link Address}, {@code cpf}/{@code rg} are additionally
     * routed through {@link BlindIndexService} in the same call, and every {@code contactChanges}
     * entry is applied via {@link ContactService} -- all inside the same transactional boundary as
     * the caller (REQ-17's atomicity). Package-private so {@link ProfileEditRequestService} (same
     * package) reuses it for approvals rather than duplicating the write.
     */
    @Transactional
    void applyFields(User target, ProfileFieldsDto fields, List<ContactChangeDto> contactChanges) {
        UserProfile profile = requireUserProfile(target);

        if (fields != null) {
            if (fields.fullName() != null) {
                profile.setFullName(fields.fullName());
            }
            if (fields.rgOrgaoEmissor() != null) {
                profile.setRgOrgaoEmissor(fields.rgOrgaoEmissor());
            }
            if (fields.birthDate() != null) {
                profile.setBirthDate(fields.birthDate());
            }
            if (fields.cpf() != null) {
                profile.setCpf(fields.cpf());
                profile.setCpfBlindIndex(blindIndexService.hmac(fields.cpf()));
            }
            if (fields.rg() != null) {
                profile.setRg(fields.rg());
                profile.setRgBlindIndex(blindIndexService.hmac(fields.rg()));
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

    private void applyAddress(User target, AddressDto addressDto) {
        Address address =
                addressRepository.findById(target.getId()).orElseGet(() -> new Address(target));

        address.setCep(addressDto.cep());
        address.setLogradouro(addressDto.logradouro());
        address.setNumero(addressDto.numero());
        address.setComplemento(addressDto.complemento());
        address.setBairro(addressDto.bairro());
        address.setCidade(addressDto.cidade());
        address.setEstado(addressDto.estado());
        if (addressDto.pais() != null) {
            address.setPais(addressDto.pais());
        }

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
                contactRepository.findByUser(user).stream()
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
                                address.getCep(),
                                address.getLogradouro(),
                                address.getNumero(),
                                address.getComplemento(),
                                address.getBairro(),
                                address.getCidade(),
                                address.getEstado(),
                                address.getPais());

        ProfileFieldsDto fields =
                new ProfileFieldsDto(
                        profile.getFullName(),
                        profile.getCpf(),
                        profile.getRg(),
                        profile.getRgOrgaoEmissor(),
                        profile.getBirthDate(),
                        addressDto,
                        contacts);

        return UserProfileDto.of(user.getId(), user.getEmail(), fields, profile.getAvatarUrl());
    }
}
