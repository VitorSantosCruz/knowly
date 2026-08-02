package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventWriter;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.audit.RequiresGlobalPermission;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.deletion.DeletionConfirmationTokenService;
import br.com.conectabyte.knowly.deletion.exception.DeletionConfirmationInvalidException;
import br.com.conectabyte.knowly.identity.ProfileCompletenessService;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.identity.UserProfileService;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.dto.AccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.ActiveTenantDto;
import br.com.conectabyte.knowly.tenancy.dto.CreateTenantRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.MemberDetailDto;
import br.com.conectabyte.knowly.tenancy.dto.MemberDto;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantSummaryDto;
import br.com.conectabyte.knowly.tenancy.exception.InvalidPaginationException;
import br.com.conectabyte.knowly.tenancy.exception.InvalidTaxIdException;
import br.com.conectabyte.knowly.tenancy.exception.InvalidTenantEditException;
import br.com.conectabyte.knowly.tenancy.exception.LastAdminRemainingException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAlreadyExistsException;
import br.com.conectabyte.knowly.tenancy.exception.TenantNotFoundException;
import br.com.conectabyte.knowly.tenancy.validation.CnpjChecksumValidator;
import br.com.conectabyte.knowly.tenancy.validation.TaxIdNormalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final DirectPermissionGrantRepository directPermissionGrantRepository;
    private final AccessGroupRepository accessGroupRepository;
    private final AccessGroupPermissionRepository accessGroupPermissionRepository;
    private final UserAccessGroupRepository userAccessGroupRepository;
    private final PermissionService permissionService;
    private final GlobalPermissionService globalPermissionService;
    private final NotificationRepository notificationRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileService userProfileService;
    private final ProfileCompletenessService profileCompletenessService;
    private final DeletionConfirmationTokenService deletionConfirmationTokenService;
    private final AuditEventWriter auditEventWriter;

    private static final String MEMBER_RESOURCE_TYPE = "tenant-member";
    private static final String PERMISSION_RESOURCE_TYPE = "tenant-permission";
    private static final String ACCESS_GROUP_RESOURCE_TYPE = "tenant-access-group";
    private static final String HARD_DELETE_RESOURCE_TYPE = "tenant-member-hard-delete";
    private static final String BATCH_RESOURCE_TYPE = "tenant-permission-batch";
    private static final String TENANT_RESOURCE_TYPE = "tenant";

    public TenantService(
            TenantRepository tenantRepository,
            TenantMembershipRepository tenantMembershipRepository,
            UserRepository userRepository,
            DirectPermissionGrantRepository directPermissionGrantRepository,
            AccessGroupRepository accessGroupRepository,
            AccessGroupPermissionRepository accessGroupPermissionRepository,
            UserAccessGroupRepository userAccessGroupRepository,
            PermissionService permissionService,
            GlobalPermissionService globalPermissionService,
            NotificationRepository notificationRepository,
            UserProfileRepository userProfileRepository,
            UserProfileService userProfileService,
            ProfileCompletenessService profileCompletenessService,
            DeletionConfirmationTokenService deletionConfirmationTokenService,
            AuditEventWriter auditEventWriter) {
        this.tenantRepository = tenantRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.userRepository = userRepository;
        this.directPermissionGrantRepository = directPermissionGrantRepository;
        this.accessGroupRepository = accessGroupRepository;
        this.accessGroupPermissionRepository = accessGroupPermissionRepository;
        this.userAccessGroupRepository = userAccessGroupRepository;
        this.permissionService = permissionService;
        this.globalPermissionService = globalPermissionService;
        this.notificationRepository = notificationRepository;
        this.userProfileRepository = userProfileRepository;
        this.userProfileService = userProfileService;
        this.profileCompletenessService = profileCompletenessService;
        this.deletionConfirmationTokenService = deletionConfirmationTokenService;
        this.auditEventWriter = auditEventWriter;
    }

    /**
     * REQ-1 (identity-profile-model-v2): every brand-new User gets an eager, empty UserProfile row.
     */
    private User createUserWithProfile(String email) {
        User user = userRepository.save(new User(email));
        userProfileRepository.save(new UserProfile(user));
        return user;
    }

    /**
     * REQ-8 (mandatory-complete-profile): same as {@link #createUserWithProfile(String)}, plus
     * immediately writing the mandatory profile field set -- used only by {@link #addMember}'s
     * brand-new-email path, so a newly created tenant member is never left incomplete.
     */
    private User createUserWithProfile(String email, MandatoryProfileFieldsDto profile) {
        User user = createUserWithProfile(email);
        userProfileService.applyMandatoryProfile(user, profile);
        return user;
    }

    /**
     * Resolves how a freshly-authenticated user's session should start out, per REQ-3/4/5.
     * Deliberately not {@code @Transactional}: this call must see the user's memberships across
     * every tenant, and — since it's scoped by user identity, not by an arbitrary listing — it's
     * safe to run through Spring Data's own default per-call transaction rather than one where
     * TenantFilterAspect has enabled the tenant filter (which would otherwise filter out every
     * membership before a tenant is even chosen).
     */
    public TenantSessionOutcome resolveSessionOutcome(User user) {
        if (isAnyStaff(user)) {
            return new TenantSessionOutcome.Staff(!profileCompletenessService.isComplete(user));
        }

        List<TenantMembership> memberships =
                tenantMembershipRepository.findByUserAndActiveTrue(user);

        if (memberships.size() == 1) {
            return new TenantSessionOutcome.AutoSelected(memberships.get(0).getTenant().getId());
        }

        return new TenantSessionOutcome.SelectionPending();
    }

    /**
     * Lists the caller's own active memberships (for the tenant picker / switch-tenant menu). Same
     * reasoning as {@link #resolveSessionOutcome}: intentionally not {@code @Transactional}, this
     * is scoped by the caller's own identity.
     */
    public List<TenantMembership> listOwnMemberships(User user) {
        return tenantMembershipRepository.findByUserAndActiveTrue(user);
    }

    /**
     * Validates that the user actually holds an active membership in the requested tenant (REQ-7).
     * Same reasoning as {@link #resolveSessionOutcome}: intentionally not {@code @Transactional},
     * the lookup is scoped by (user, tenant) explicitly.
     */
    public TenantMembership requireActiveMembership(User user, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        TenantMembership membership =
                tenantMembershipRepository
                        .findByUserAndTenant(user, tenant)
                        .filter(TenantMembership::isActive)
                        .orElseThrow(TenantAccessDeniedException::new);
        requireNotSoftDeleted(membership.getTenant());

        return membership;
    }

    /**
     * The caller's session-derived active tenant (bug fix: {@code GET /api/tenants/active} was
     * previously missing, so a staff user acting as a tenant -- which sets the session attribute
     * without any real {@code TenantMembership} row, per DECISIONS.md -- had no way to learn which
     * tenant is active after e.g. a page reload). {@code role} is only populated when a real
     * membership row exists for (user, tenant); staff acting as a tenant get {@code null}.
     */
    @Transactional(readOnly = true)
    public ActiveTenantDto getActiveTenant(User user, Long tenantId) {
        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);
        requireNotSoftDeleted(tenant);
        MembershipRole role =
                tenantMembershipRepository
                        .findByUserAndTenant(user, tenant)
                        .filter(TenantMembership::isActive)
                        .map(TenantMembership::getRole)
                        .orElse(null);

        return ActiveTenantDto.from(tenant, role);
    }

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Every tenant in the system — staff-only, powers a staff "act as this tenant" picker (staff
     * have no memberships of their own to pick from, unlike a regular multi-membership user).
     * specify/features/tenant-pagination-search/SPEC.md REQ-1/2/3/4/5/6/7/8/9: DB-level pagination
     * plus an optional cross-field ({@code name}/{@code cnpj}/{@code razaoSocial}) search, fixed
     * server-side sort by {@code name} ascending. Negative {@code page} or {@code size <= 0} are
     * rejected before an over-{@value #MAX_PAGE_SIZE} {@code size} is clamped, so an
     * out-of-range-and-negative value like {@code size=-500} is rejected, not silently clamped.
     */
    @Transactional(readOnly = true)
    public PageResponseDto<TenantSummaryDto> listAllTenants(
            User actor, int page, int size, String search) {
        requireStaff(actor, GlobalPermission.TENANT_ACT_AS_ANY);

        if (page < 0 || size <= 0) {
            throw new InvalidPaginationException("page must be >= 0 and size must be > 0");
        }

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by("name").ascending());

        return PageResponseDto.from(
                tenantRepository.search(search, pageable).map(TenantSummaryDto::from));
    }

    /**
     * tenant-crud REQ-20/REQ-21 (product owner decision 2026-08-02): every soft-deleted tenant, for
     * the separate "deactivated tenants" listing -- gated by {@code TENANT_DELETE} (which per the
     * house rule already requires {@code TENANT_VIEW}), not {@code TENANT_ACT_AS_ANY}, since this
     * is closer to an audit/deletion-history concern than the "act as this tenant" picker {@link
     * #listAllTenants} powers. Same pagination/search/sort shape as {@link #listAllTenants}.
     */
    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)
    public PageResponseDto<TenantSummaryDto> listDeactivatedTenants(
            User actor, int page, int size, String search) {
        if (page < 0 || size <= 0) {
            throw new InvalidPaginationException("page must be >= 0 and size must be > 0");
        }

        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by("name").ascending());

        return PageResponseDto.from(
                tenantRepository.searchDeactivated(search, pageable).map(TenantSummaryDto::from));
    }

    /** Confirms a tenant exists, for staff switching to act as it without holding a membership. */
    @Transactional(readOnly = true)
    public Tenant requireTenant(User actor, Long tenantId) {
        requireStaff(actor, GlobalPermission.TENANT_ACT_AS_ANY);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);
        requireNotSoftDeleted(tenant);

        return tenant;
    }

    /**
     * REQ-11 (tenant-crud): a soft-deleted tenant is rejected the same way "no access" already is,
     * at every switch-time chokepoint. See {@code TenantFilterAspect} for the complementary,
     * ongoing-session-lifetime check this alone does not cover.
     */
    private void requireNotSoftDeleted(Tenant tenant) {
        if (tenant.getDeletedAt() != null) {
            throw new TenantAccessDeniedException();
        }
    }

    /**
     * The caller's own effective permissions in their active tenant — lets the frontend hide
     * actions it can't perform instead of showing them and letting a 403 explain why. {@code
     * STAFF_ADMIN} gets every permission, consistent with {@code PermissionAspect} bypassing the
     * check for them; {@code MEMBER_ADMIN} of the active tenant likewise gets every permission,
     * consistent with {@code PermissionAspect} bypassing the check for them in their own tenant
     * (see {@code member-admin-tenant-bypass}). A permission-gated {@code STAFF}/{@code MEMBER}
     * user goes through the normal membership-based check like anyone else.
     */
    @Transactional(readOnly = true)
    public List<Permission> ownEffectivePermissions(User user, Long tenantId, boolean staffAdmin) {
        if (staffAdmin) {
            return List.of(Permission.values());
        }

        TenantMembership membership = requireActiveMembership(user, tenantId);

        if (membership.getRole() == MembershipRole.MEMBER_ADMIN) {
            return List.of(Permission.values());
        }

        return List.copyOf(permissionService.effectivePermissions(membership));
    }

    /**
     * REQ-10 (tenancy), REQ-1..REQ-9 (tenant-creation): only staff create tenants, and always
     * atomically with a first admin -- full company identification plus the first admin's complete
     * mandatory profile in the same transaction (2026-08-02 amendment, see
     * specify/features/tenant-creation/PLAN.md's "Atomicity" section). {@code request.adminEmail()}
     * must be brand new -- unlike {@link #addMember}, which reuses an existing account, tenant
     * creation's first admin is rejected with {@link TenantAlreadyExistsException} (409) if the
     * email is already taken, per this PLAN's API contract table.
     *
     * <p>Both {@code taxId} and {@code adminEmail} are checked proactively, before any insert --
     * deliberately not the "insert, catch {@code DataIntegrityViolationException}" pattern {@code
     * ProfileEditRequestService#approveEditRequest} uses (documented deviation from PLAN.md's
     * original proposal): unlike that call site, this whole method is itself the
     * {@code @Transactional} boundary carrying its own {@code @AuditLog}, so a failed insert here
     * leaves the persistence context in a state Hibernate can't safely continue flushing against
     * within the same still-open transaction (observed as a spurious {@code AssertionFailure: ...
     * has a null identifier} when the audit write attempted its own {@code REQUIRES_NEW} flush
     * afterward). A proactive existence check has the same small TOCTOU window {@code adminEmail}'s
     * check already accepted, and sidesteps the corrupted-session failure entirely.
     */
    @Transactional
    @AuditLog(action = "tenant.create", resourceType = "Tenant")
    public Tenant createTenant(User actor, CreateTenantRequestDto request) {
        requireStaff(actor, GlobalPermission.TENANT_CREATE);

        if (userRepository.findByEmailIgnoreCase(request.adminEmail()).isPresent()) {
            throw new TenantAlreadyExistsException();
        }

        // REQ-6a/REQ-6d: normalize (and, for Brazil, checksum-validate) `taxId` BEFORE the
        // duplicate check below, and use the normalized value for that check and for persistence --
        // otherwise two submissions of the same CNPJ differing only in punctuation would both look
        // like distinct values to `existsByTaxIdAndDeletedAtIsNull` and only collide at the DB's
        // unique index.
        String normalizedTaxId = TaxIdNormalizer.normalize(request.taxId());
        if (isBrazil(request.country()) && !CnpjChecksumValidator.isValid(normalizedTaxId)) {
            throw new InvalidTaxIdException();
        }

        if (tenantRepository.existsByTaxIdAndDeletedAtIsNull(normalizedTaxId)) {
            throw new TenantAlreadyExistsException();
        }

        Tenant tenant =
                new Tenant(
                        request.name(),
                        request.legalName(),
                        normalizedTaxId,
                        request.country(),
                        request.contactEmail(),
                        request.contactPhone(),
                        request.address().postalCode(),
                        request.address().street(),
                        request.address().number(),
                        request.address().complement(),
                        request.address().neighborhood(),
                        request.address().city(),
                        request.address().state());
        tenant = tenantRepository.save(tenant);

        User admin = createUserWithProfile(request.adminEmail(), request.profile());
        MembershipRole role = request.role() == null ? MembershipRole.MEMBER_ADMIN : request.role();
        tenantMembershipRepository.save(new TenantMembership(admin, tenant, role));

        return tenant;
    }

    /**
     * tenant-crud REQ-1/REQ-2/REQ-3/REQ-4/REQ-6: staff-only (no {@code MEMBER_ADMIN} bypass, unlike
     * {@link #addMember} and friends -- reuses {@code @RequiresGlobalPermission} directly per
     * PLAN.md's "Architectural decisions" rather than {@link #requireStaff}, so the {@code
     * TENANT_VIEW} view-dependency check REQ-4/REQ-5 require is not silently skipped). Every field
     * is independently optional (REQ-1): {@code null} leaves the current value unchanged,
     * present-but- blank is rejected (REQ-2). {@code taxId} is never accepted (REQ-3) -- {@link
     * EditTenantRequestDto} has no such field at all.
     */
    @Transactional
    @RequiresGlobalPermission(GlobalPermission.TENANT_EDIT)
    @AuditLog(action = "tenant.edit", resourceType = "Tenant")
    public TenantSummaryDto editTenant(User actor, Long tenantId, EditTenantRequestDto request) {
        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .filter(t -> t.getDeletedAt() == null)
                        .orElseThrow(TenantNotFoundException::new);

        requireNotBlankIfPresent("name", request.name());
        requireNotBlankIfPresent("legalName", request.legalName());
        requireNotBlankIfPresent("contactEmail", request.contactEmail());
        requireNotBlankIfPresent("contactPhone", request.contactPhone());
        requireNotBlankIfPresent("postalCode", request.postalCode());
        requireNotBlankIfPresent("street", request.street());
        requireNotBlankIfPresent("number", request.number());
        requireNotBlankIfPresent("neighborhood", request.neighborhood());
        requireNotBlankIfPresent("city", request.city());
        requireNotBlankIfPresent("state", request.state());

        if (request.name() != null) {
            tenant.setName(request.name());
        }
        if (request.legalName() != null) {
            tenant.setLegalName(request.legalName());
        }
        if (request.contactEmail() != null) {
            tenant.setContactEmail(request.contactEmail());
        }
        if (request.contactPhone() != null) {
            tenant.setContactPhone(request.contactPhone());
        }
        if (request.postalCode() != null) {
            tenant.setPostalCode(request.postalCode());
        }
        if (request.street() != null) {
            tenant.setStreet(request.street());
        }
        if (request.number() != null) {
            tenant.setNumber(request.number());
        }
        if (request.complement() != null) {
            tenant.setComplement(request.complement());
        }
        if (request.neighborhood() != null) {
            tenant.setNeighborhood(request.neighborhood());
        }
        if (request.city() != null) {
            tenant.setCity(request.city());
        }
        if (request.state() != null) {
            tenant.setState(request.state());
        }

        return TenantSummaryDto.from(tenantRepository.save(tenant));
    }

    /**
     * REQ-2: a present field must not be blankable -- {@code null} (omitted) is not checked here.
     */
    private void requireNotBlankIfPresent(String fieldName, String value) {
        if (value != null && value.isBlank()) {
            throw new InvalidTenantEditException(fieldName + " must not be blank");
        }
    }

    /**
     * tenant-crud REQ-13/REQ-15: generation endpoint reuses the exact same guard as {@link
     * #deleteTenant} -- {@code DeletionConfirmationTokenService} reused verbatim, a sixth {@code
     * resourceType} ("tenant") with a single scalar {@code resourceId} (the tenant id), per
     * PLAN.md.
     */
    @Transactional(readOnly = true)
    @RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)
    public String generateTenantDeletionConfirmationToken(
            User actor, Long tenantId, String acceptLanguageHeaderValue) {
        return deletionConfirmationTokenService.generate(
                TENANT_RESOURCE_TYPE, tenantId.toString(), actor, acceptLanguageHeaderValue);
    }

    /**
     * tenant-crud REQ-8/REQ-9/REQ-10/REQ-14/REQ-16: soft-deletes the tenant and, atomically in the
     * same transaction, deactivates every one of its currently-active memberships (REQ-9) via a
     * bulk update rather than a per-row loop (REQ-18: no volume-based blocking, must stay cheap
     * regardless of membership count). {@code Article}/{@code Conversation}/{@code AccessGroup}/
     * permission-grant rows are untouched by construction (REQ-10) -- nothing here reaches those
     * tables.
     */
    @Transactional
    @RequiresGlobalPermission(GlobalPermission.TENANT_DELETE)
    @AuditLog(action = "tenant.delete", resourceType = "Tenant")
    public void deleteTenant(User actor, Long tenantId, String word) {
        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .filter(t -> t.getDeletedAt() == null)
                        .orElseThrow(TenantNotFoundException::new);

        if (!deletionConfirmationTokenService.validateAndConsume(
                TENANT_RESOURCE_TYPE, tenantId.toString(), actor, word)) {
            throw new DeletionConfirmationInvalidException();
        }

        tenant.setDeletedAt(Instant.now());
        // saveAndFlush, not save: deactivateAllByTenant below clears the persistence context
        // (@Modifying(clearAutomatically = true)) -- this write must be flushed to the DB first or
        // it would be silently discarded by that clear rather than committed.
        tenantRepository.saveAndFlush(tenant);
        tenantMembershipRepository.deactivateAllByTenant(tenant);
    }

    /**
     * REQ-9/16: tenant admin (own tenant) or staff (any tenant) can add members. Per REQ-1/REQ-1a:
     * a user who already has an account is added as a pending membership requiring their acceptance
     * (REQ-4 notification); a brand-new email is created active immediately, matching today's
     * behavior, since there is no existing account to notify/ask for consent.
     *
     * <p>REQ-8 (mandatory-complete-profile): {@code profile} is required on every call (enforced by
     * {@code AddMemberRequestDto}'s Bean Validation before this method is ever entered), but is
     * only written when a brand-new {@link User} is created here -- an already-existing account's
     * profile is never silently overwritten by an inviter's submission, preserving
     * identity-profile-model-v2's existing self-request/approval requirement for changes to an
     * already-set field (conservative reading: this SPEC's completeness requirement is a
     * creation-time precondition, not a license to bypass that approval flow for pre-existing
     * users).
     */
    @Transactional
    @AuditLog(
            action = "tenant.member.add",
            resourceType = "TenantMembership",
            metadataExpression = "#role")
    public TenantMembership addMember(
            User actor,
            Long tenantId,
            String email,
            MembershipRole role,
            MandatoryProfileFieldsDto profile) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_MEMBER_CREATE);
        MembershipRole resolvedRole = role == null ? MembershipRole.MEMBER : role;

        if (resolvedRole == MembershipRole.MEMBER_ADMIN) {
            requireCallerIsAdminOfTenant(actor, tenantId);
        }

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);
        Optional<User> existingUser = userRepository.findByEmailIgnoreCase(email);
        requireNotSelfTarget(actor, existingUser.map(User::getId).orElse(null));
        boolean userAlreadyExisted = existingUser.isPresent();
        User user = existingUser.orElseGet(() -> createUserWithProfile(email, profile));

        TenantMembership membership =
                tenantMembershipRepository
                        .findByUserAndTenant(user, tenant)
                        .orElseGet(() -> new TenantMembership(user, tenant, resolvedRole));
        membership.setRole(resolvedRole);

        if (userAlreadyExisted) {
            membership.setStatus(MembershipStatus.PENDING);
            membership.setActive(false);
        } else {
            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setActive(true);
        }

        TenantMembership saved = tenantMembershipRepository.save(membership);

        if (userAlreadyExisted) {
            notificationRepository.save(
                    new Notification(user, NotificationType.MEMBERSHIP_INVITATION_PENDING, saved));
        }

        return saved;
    }

    /** REQ-17: generation endpoint reuses the exact same guard as {@link #removeMember}. */
    @Transactional(readOnly = true)
    public String generateMemberRemovalDeletionConfirmationToken(
            User actor, Long tenantId, Long membershipId, String acceptLanguageHeaderValue) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_MEMBER_DELETE);

        return deletionConfirmationTokenService.generate(
                MEMBER_RESOURCE_TYPE, membershipId.toString(), actor, acceptLanguageHeaderValue);
    }

    /** REQ-9/16/19: always a soft removal, never a hard delete; requires a valid REQ-16 token. */
    @Transactional
    @AuditLog(action = "tenant.member.remove", resourceType = "TenantMembership")
    public void removeMember(User actor, Long tenantId, Long membershipId, String word) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_MEMBER_DELETE);

        if (!deletionConfirmationTokenService.validateAndConsume(
                MEMBER_RESOURCE_TYPE, membershipId.toString(), actor, word)) {
            throw new DeletionConfirmationInvalidException();
        }

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        requireNotSelfTarget(actor, membership.getUser().getId());
        membership.setActive(false);
        tenantMembershipRepository.save(membership);
    }

    /** REQ-14/16: direct permission grant, admin (own tenant) or staff only. */
    @Transactional
    @AuditLog(action = "tenant.permission.grant", resourceType = "DirectPermissionGrant")
    public void grantPermission(
            User actor, Long tenantId, Long membershipId, Permission permission) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_CREATE);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        requireNotSelfTarget(actor, membership.getUser().getId());
        rejectAdminTarget(membership);
        directPermissionGrantRepository
                .findByTenantMembershipAndPermission(membership, permission)
                .orElseGet(
                        () ->
                                directPermissionGrantRepository.save(
                                        new DirectPermissionGrant(membership, permission)));
    }

    /** REQ-20: generation endpoint reuses the exact same guard as {@link #revokePermission}. */
    @Transactional(readOnly = true)
    public String generatePermissionRevocationDeletionConfirmationToken(
            User actor,
            Long tenantId,
            Long membershipId,
            Permission permission,
            String acceptLanguageHeaderValue) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);

        return deletionConfirmationTokenService.generate(
                PERMISSION_RESOURCE_TYPE,
                permissionResourceId(membershipId, permission),
                actor,
                acceptLanguageHeaderValue);
    }

    /** REQ-14/16/19: revoke a direct permission grant, admin (own tenant) or staff only. */
    @Transactional
    @AuditLog(action = "tenant.permission.revoke", resourceType = "DirectPermissionGrant")
    public void revokePermission(
            User actor, Long tenantId, Long membershipId, Permission permission, String word) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);

        if (!deletionConfirmationTokenService.validateAndConsume(
                PERMISSION_RESOURCE_TYPE,
                permissionResourceId(membershipId, permission),
                actor,
                word)) {
            throw new DeletionConfirmationInvalidException();
        }

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        requireNotSelfTarget(actor, membership.getUser().getId());
        rejectAdminTarget(membership);
        directPermissionGrantRepository
                .findByTenantMembershipAndPermission(membership, permission)
                .ifPresent(directPermissionGrantRepository::delete);
    }

    private String permissionResourceId(Long membershipId, Permission permission) {
        return membershipId + ":" + permission;
    }

    private String accessGroupResourceId(Long membershipId, Long accessGroupId) {
        return membershipId + ":" + accessGroupId;
    }

    /** REQ-13: tenant-scoped, admin-defined access group. */
    @Transactional
    @AuditLog(action = "tenant.access_group.create", resourceType = "AccessGroup")
    public AccessGroup createAccessGroup(User actor, Long tenantId, String name) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_ACCESS_GROUP_CREATE);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);

        return accessGroupRepository.save(new AccessGroup(tenant, name));
    }

    /** REQ-13/16: assign a permission to an access group. */
    @Transactional
    @AuditLog(
            action = "tenant.access_group.grant_permission",
            resourceType = "AccessGroupPermission")
    public void grantAccessGroupPermission(
            User actor, Long tenantId, Long accessGroupId, Permission permission) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_ACCESS_GROUP_EDIT);

        AccessGroup accessGroup =
                accessGroupRepository
                        .findById(accessGroupId)
                        .orElseThrow(TenantAccessDeniedException::new);
        accessGroupPermissionRepository
                .findByAccessGroupAndPermission(accessGroup, permission)
                .orElseGet(
                        () ->
                                accessGroupPermissionRepository.save(
                                        new AccessGroupPermission(accessGroup, permission)));
    }

    /** REQ-14: assign a membership to an access group, taking effect immediately. */
    @Transactional
    @AuditLog(action = "tenant.member.access_group.assign", resourceType = "UserAccessGroup")
    public void assignAccessGroup(
            User actor, Long tenantId, Long membershipId, Long accessGroupId) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_CREATE);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        requireNotSelfTarget(actor, membership.getUser().getId());
        rejectAdminTarget(membership);
        AccessGroup accessGroup =
                accessGroupRepository
                        .findById(accessGroupId)
                        .orElseThrow(TenantAccessDeniedException::new);

        userAccessGroupRepository
                .findByTenantMembershipAndAccessGroup(membership, accessGroup)
                .orElseGet(
                        () ->
                                userAccessGroupRepository.save(
                                        new UserAccessGroup(membership, accessGroup)));
    }

    /** REQ-9/16: list a tenant's active members — admin (own tenant) or staff only. */
    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(User actor, Long tenantId) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_MEMBER_VIEW);

        return tenantMembershipRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(MemberDto::from)
                .toList();
    }

    /** REQ-13: list a tenant's access groups — admin (own tenant) or staff only. */
    @Transactional(readOnly = true)
    public List<AccessGroupDto> listAccessGroups(User actor, Long tenantId) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_ACCESS_GROUP_VIEW);

        Tenant tenant =
                tenantRepository.findById(tenantId).orElseThrow(TenantAccessDeniedException::new);

        return accessGroupRepository.findByTenant(tenant).stream()
                .map(AccessGroupDto::from)
                .toList();
    }

    /**
     * REQ-15/16: a member's direct/group/effective permissions — admin (own tenant) or staff only.
     */
    @Transactional(readOnly = true)
    public MemberDetailDto getMemberDetail(User actor, Long tenantId, Long membershipId) {
        requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_VIEW);

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);

        List<Permission> direct =
                directPermissionGrantRepository.findByTenantMembership(membership).stream()
                        .map(DirectPermissionGrant::getPermission)
                        .toList();
        List<AccessGroupDto> groups =
                userAccessGroupRepository.findByTenantMembership(membership).stream()
                        .map(UserAccessGroup::getAccessGroup)
                        .map(AccessGroupDto::from)
                        .toList();
        List<Permission> effective =
                permissionService.effectivePermissions(membership).stream().toList();
        boolean isLastAdminOfType =
                membership.getRole() == MembershipRole.MEMBER_ADMIN
                        && tenantMembershipRepository.countByTenantIdAndRoleAndActiveTrue(
                                        tenantId, MembershipRole.MEMBER_ADMIN)
                                == 1;

        return new MemberDetailDto(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getEmail(),
                membership.getRole(),
                direct,
                groups,
                effective,
                isLastAdminOfType);
    }

    /** REQ-23: generation endpoint reuses the exact same guard as {@link #unassignAccessGroup}. */
    @Transactional(readOnly = true)
    public String generateAccessGroupUnassignmentDeletionConfirmationToken(
            User actor,
            Long tenantId,
            Long membershipId,
            Long accessGroupId,
            String acceptLanguageHeaderValue) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);

        return deletionConfirmationTokenService.generate(
                ACCESS_GROUP_RESOURCE_TYPE,
                accessGroupResourceId(membershipId, accessGroupId),
                actor,
                acceptLanguageHeaderValue);
    }

    /**
     * REQ-14/16/22: unassign a membership from an access group, admin (own tenant) or staff only.
     */
    @Transactional
    @AuditLog(action = "tenant.member.access_group.unassign", resourceType = "UserAccessGroup")
    public void unassignAccessGroup(
            User actor, Long tenantId, Long membershipId, Long accessGroupId, String word) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_DELETE);

        if (!deletionConfirmationTokenService.validateAndConsume(
                ACCESS_GROUP_RESOURCE_TYPE,
                accessGroupResourceId(membershipId, accessGroupId),
                actor,
                word)) {
            throw new DeletionConfirmationInvalidException();
        }

        TenantMembership membership =
                tenantMembershipRepository
                        .findById(membershipId)
                        .orElseThrow(TenantAccessDeniedException::new);
        requireNotSelfTarget(actor, membership.getUser().getId());
        AccessGroup accessGroup =
                accessGroupRepository
                        .findById(accessGroupId)
                        .orElseThrow(TenantAccessDeniedException::new);

        userAccessGroupRepository
                .findByTenantMembershipAndAccessGroup(membership, accessGroup)
                .ifPresent(userAccessGroupRepository::delete);
    }

    /**
     * REQ-2/REQ-6/REQ-21/REQ-22: {@code MEMBER_ADMIN} -> {@code MEMBER} within {@code tenantId},
     * rejected (409) if the target is the last {@code MEMBER_ADMIN} in that tenant (per-tenant
     * floor, locked count) or is the caller themselves. A target who isn't currently {@code
     * MEMBER_ADMIN} is a no-op.
     */
    @Transactional
    @AuditLog(
            action = "tenant.member.demote",
            resourceType = "TenantMembership",
            resourceIdExpression = "#membershipId")
    public void demoteMember(User actor, Long tenantId, Long membershipId) {
        requireCallerIsAdminOfTenant(actor, tenantId);
        TenantMembership membership = requireMembership(membershipId);
        requireNotSelfTarget(actor, membership.getUser().getId());

        if (membership.getRole() != MembershipRole.MEMBER_ADMIN) {
            return;
        }

        requireNotLastMemberAdmin(tenantId, membershipId);
        membership.setRole(MembershipRole.MEMBER);
        tenantMembershipRepository.save(membership);
    }

    /**
     * REQ-24/REQ-28/REQ-29: {@code MEMBER} -> {@code MEMBER_ADMIN} within {@code tenantId}, no
     * floor/ceiling check, rejected only if the caller isn't an admin of that tenant or targets
     * themselves.
     */
    @Transactional
    @AuditLog(
            action = "tenant.member.promote",
            resourceType = "TenantMembership",
            resourceIdExpression = "#membershipId")
    public void promoteMember(User actor, Long tenantId, Long membershipId) {
        requireCallerIsAdminOfTenant(actor, tenantId);
        TenantMembership membership = requireMembership(membershipId);
        requireNotSelfTarget(actor, membership.getUser().getId());

        membership.setRole(MembershipRole.MEMBER_ADMIN);
        tenantMembershipRepository.save(membership);
    }

    /** REQ-9: generation endpoint reuses the exact same guard as {@link #hardDeleteMember}. */
    @Transactional(readOnly = true)
    public String generateMemberHardDeletionConfirmationToken(
            User actor, Long tenantId, Long membershipId, String acceptLanguageHeaderValue) {
        TenantMembership membership = requireMembership(membershipId);
        requireHardDeleteGate(actor, tenantId, membership);

        return deletionConfirmationTokenService.generate(
                HARD_DELETE_RESOURCE_TYPE,
                membershipId.toString(),
                actor,
                acceptLanguageHeaderValue);
    }

    /**
     * REQ-7/8/10/11: hard delete, requires a valid deletion-confirmation token, rejects self-target
     * and the last {@code MEMBER_ADMIN} in the tenant (locked count); never blocked for a plain
     * {@code MEMBER} target (including a tenant's lone {@code MEMBER}). Dependent grant/group rows
     * are removed by the existing {@code ON DELETE CASCADE} FK (PLAN.md's "Data schema").
     */
    @Transactional
    @AuditLog(
            action = "tenant.member.hard_delete",
            resourceType = "TenantMembership",
            resourceIdExpression = "#membershipId")
    public void hardDeleteMember(User actor, Long tenantId, Long membershipId, String word) {
        TenantMembership membership = requireMembership(membershipId);
        requireHardDeleteGate(actor, tenantId, membership);
        requireNotSelfTarget(actor, membership.getUser().getId());

        if (!deletionConfirmationTokenService.validateAndConsume(
                HARD_DELETE_RESOURCE_TYPE, membershipId.toString(), actor, word)) {
            throw new DeletionConfirmationInvalidException();
        }

        if (membership.getRole() == MembershipRole.MEMBER_ADMIN) {
            requireNotLastMemberAdmin(tenantId, membershipId);
        }

        tenantMembershipRepository.delete(membership);
    }

    /**
     * REQ-16: an admin-tier target (own tenant) requires {@link #requireCallerIsAdminOfTenant}; a
     * plain-{@code MEMBER} target follows the same gate as {@link #removeMember} (matching it per
     * PLAN.md's AppSec addition) — {@code TENANT_MEMBER_DELETE}, reconnected here from the
     * pre-{@code permission-granularity-model} {@code TENANT_MEMBER_MANAGE_ANY} fallback documented
     * in {@code staff-rbac-management-operations}'s "Implementation notes" now that the granular
     * permission exists.
     */
    private void requireHardDeleteGate(User actor, Long tenantId, TenantMembership membership) {
        if (membership.getRole() == MembershipRole.MEMBER_ADMIN) {
            requireCallerIsAdminOfTenant(actor, tenantId);
        } else {
            requireAdminOfTenantOrStaff(actor, tenantId, GlobalPermission.TENANT_MEMBER_DELETE);
        }
    }

    /**
     * REQ-16: generation endpoint reuses the exact same guard as {@link #batchUpdatePermissions}.
     */
    @Transactional(readOnly = true)
    public String generateBatchPermissionUpdateDeletionConfirmationToken(
            User actor, Long tenantId, Long membershipId, String acceptLanguageHeaderValue) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_CREATE);

        return deletionConfirmationTokenService.generate(
                BATCH_RESOURCE_TYPE, membershipId.toString(), actor, acceptLanguageHeaderValue);
    }

    /**
     * Tenant-scope counterpart of {@link StaffService#batchUpdatePermissions(Long, Set, String)} —
     * same full-replacement/no-op/per-permission-audit-event/admin-target-rejection semantics,
     * scoped to {@code tenantId}'s directly-granted {@code Permission} set. Gate reconnected from
     * the pre-{@code permission-granularity-model} {@code TENANT_PERMISSION_GRANT_MANAGE_ANY}
     * fallback to {@code TENANT_PERMISSION_GRANT_CREATE} (mirroring {@link #grantPermission}, per
     * {@code staff-rbac-management-operations} PLAN.md's REQ-16 gate note), now that the granular
     * permission exists.
     */
    @Transactional
    public void batchUpdatePermissions(
            User actor,
            Long tenantId,
            Long membershipId,
            Set<Permission> permissions,
            String word) {
        requireAdminOfTenantOrStaff(
                actor, tenantId, GlobalPermission.TENANT_PERMISSION_GRANT_CREATE);
        TenantMembership membership = requireMembership(membershipId);
        requireNotSelfTarget(actor, membership.getUser().getId());
        rejectAdminTarget(membership);

        Set<Permission> current =
                new HashSet<>(
                        directPermissionGrantRepository.findByTenantMembership(membership).stream()
                                .map(DirectPermissionGrant::getPermission)
                                .toList());
        Set<Permission> submitted = permissions == null ? Set.of() : permissions;

        Set<Permission> added = new HashSet<>(submitted);
        added.removeAll(current);
        Set<Permission> removed = new HashSet<>(current);
        removed.removeAll(submitted);

        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }

        if (!deletionConfirmationTokenService.validateAndConsume(
                BATCH_RESOURCE_TYPE, membershipId.toString(), actor, word)) {
            throw new DeletionConfirmationInvalidException();
        }

        for (Permission permission : added) {
            directPermissionGrantRepository.save(new DirectPermissionGrant(membership, permission));
            writeBatchAuditEvent(actor, tenantId, membershipId, "grant", permission);
        }

        for (Permission permission : removed) {
            directPermissionGrantRepository
                    .findByTenantMembershipAndPermission(membership, permission)
                    .ifPresent(directPermissionGrantRepository::delete);
            writeBatchAuditEvent(actor, tenantId, membershipId, "revoke", permission);
        }
    }

    private void writeBatchAuditEvent(
            User actor, Long tenantId, Long membershipId, String change, Permission permission) {
        auditEventWriter.write(
                new AuditEvent(
                        actor.getId(),
                        tenantId,
                        "tenant.permission.batch_update",
                        "DirectPermissionGrant",
                        membershipId + ":" + change + ":" + permission,
                        AuditOutcome.SUCCESS));
    }

    private TenantMembership requireMembership(Long membershipId) {
        return tenantMembershipRepository
                .findById(membershipId)
                .orElseThrow(TenantAccessDeniedException::new);
    }

    /**
     * REQ-17/18/19: rejects any grant/revoke/access-group/batch-update mutation whose target is a
     * {@code MEMBER_ADMIN} — demote/hard-delete are the only paths allowed to touch an admin-tier
     * target.
     */
    private void rejectAdminTarget(TenantMembership membership) {
        if (membership.getRole() == MembershipRole.MEMBER_ADMIN) {
            throw new PermissionDeniedException();
        }
    }

    /**
     * Locks every current active {@code MEMBER_ADMIN} membership of {@code tenantId} (including
     * {@code targetMembershipId}'s, if still one) and rejects if none of the others remain — closes
     * the TOCTOU window a plain {@code COUNT} read-then-write would leave open (PLAN.md).
     */
    private void requireNotLastMemberAdmin(Long tenantId, Long targetMembershipId) {
        List<TenantMembership> admins =
                tenantMembershipRepository.findByTenantIdAndRoleAndActiveTrueForUpdate(
                        tenantId, MembershipRole.MEMBER_ADMIN);
        boolean anyOtherAdminRemains =
                admins.stream().anyMatch(admin -> !admin.getId().equals(targetMembershipId));

        if (!anyOtherAdminRemains) {
            throw new LastAdminRemainingException();
        }
    }

    /**
     * REQ-4 (member-admin-tenant-bypass): no user — regardless of role — may alter their own role
     * or their own permission/access-group grants — nor remove their own membership — even through
     * the {@code MEMBER_ADMIN} bypass in {@link #requireAdminOfTenantOrStaff}. Called after the
     * target user/membership is resolved and before any mutation in {@code addMember}/{@code
     * removeMember}/{@code grantPermission}/{@code revokePermission}/{@code assignAccessGroup}/
     * {@code unassignAccessGroup}.
     */
    private void requireNotSelfTarget(User actor, Long targetUserId) {
        if (targetUserId != null && targetUserId.equals(actor.getId())) {
            throw new PermissionDeniedException();
        }
    }

    private boolean isAnyStaff(User user) {
        return user.getGlobalRole() == GlobalRole.STAFF_ADMIN
                || user.getGlobalRole() == GlobalRole.STAFF;
    }

    /**
     * {@code STAFF_ADMIN} always passes unconditionally; a permission-gated {@code STAFF} user
     * passes only if granted {@code requiredPermission} (directly or via a global access group);
     * anyone else is rejected outright.
     */
    private void requireStaff(User actor, GlobalPermission requiredPermission) {
        if (actor.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            return;
        }

        if (actor.getGlobalRole() == GlobalRole.STAFF
                && globalPermissionService.hasPermission(actor, requiredPermission)) {
            return;
        }

        throw new PermissionDeniedException();
    }

    private static final Set<String> BRAZIL_COUNTRY_LITERALS = Set.of("br", "brazil", "brasil");

    /** REQ-6/REQ-6c: matches {@code TaxIdValidator}'s own Brazil-literal detection. */
    private static boolean isBrazil(String country) {
        return country != null && BRAZIL_COUNTRY_LITERALS.contains(country.trim().toLowerCase());
    }

    /**
     * REQ-7/REQ-8 (user-role-selection-at-creation): only a {@code STAFF_ADMIN} or that same
     * tenant's active {@code MEMBER_ADMIN} may create a new {@code MEMBER_ADMIN} membership -- no
     * permission-grant substitution, unlike {@link #requireAdminOfTenantOrStaff}, which also lets a
     * granted {@code STAFF}/{@code MEMBER} through and is deliberately not reused here. Reused
     * verbatim by {@code staff-rbac-management-operations}'s demotion/deletion/promotion paths, per
     * PLAN.md.
     */
    private void requireCallerIsAdminOfTenant(User actor, Long tenantId) {
        if (actor.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        boolean isAdminOfTenant =
                tenantMembershipRepository
                        .findByUserAndTenant(actor, tenant)
                        .filter(TenantMembership::isActive)
                        .filter(membership -> membership.getRole() == MembershipRole.MEMBER_ADMIN)
                        .isPresent();

        if (!isAdminOfTenant) {
            throw new PermissionDeniedException();
        }
    }

    /**
     * REQ-9/16: staff can manage any tenant (STAFF_ADMIN unconditionally, STAFF only if granted
     * {@code requiredPermission}); a tenant admin only their own. permission-granularity-model
     * REQ-2: a granted {@code STAFF} caller must also hold {@code
     * requiredPermission.viewDependency()}, if any, mirroring {@code GlobalPermissionAspect}'s
     * check for annotation-driven endpoints (this helper isn't annotation-driven, so it re-derives
     * the same rule from the same authoritative source, {@link GlobalPermission#viewDependency()}).
     */
    private void requireAdminOfTenantOrStaff(
            User actor, Long tenantId, GlobalPermission requiredPermission) {
        if (actor.getGlobalRole() == GlobalRole.STAFF_ADMIN) {
            return;
        }

        if (actor.getGlobalRole() == GlobalRole.STAFF
                && globalPermissionService.hasPermission(actor, requiredPermission)
                && requiredPermission
                        .viewDependency()
                        .map(view -> globalPermissionService.hasPermission(actor, view))
                        .orElse(true)) {
            return;
        }

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        boolean isAdminOfTenant =
                tenantMembershipRepository
                        .findByUserAndTenant(actor, tenant)
                        .filter(TenantMembership::isActive)
                        .filter(membership -> membership.getRole() == MembershipRole.MEMBER_ADMIN)
                        .isPresent();

        if (!isAdminOfTenant) {
            throw new PermissionDeniedException();
        }
    }
}
