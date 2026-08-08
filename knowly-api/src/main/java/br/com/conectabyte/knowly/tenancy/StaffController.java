package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AuthenticatedUserNotFoundException;
import br.com.conectabyte.knowly.deletion.dto.DeleteConfirmationRequestDto;
import br.com.conectabyte.knowly.deletion.dto.DeletionConfirmationTokenDto;
import br.com.conectabyte.knowly.tenancy.dto.AuditEventDto;
import br.com.conectabyte.knowly.tenancy.dto.BatchPermissionUpdateRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.CreateGlobalAccessGroupRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.CreateStaffUserRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.GlobalAccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.GlobalPermissionRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.OwnGlobalPermissionsDto;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import br.com.conectabyte.knowly.tenancy.dto.StaffUserDetailDto;
import br.com.conectabyte.knowly.tenancy.dto.StaffUserSummaryDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;
    private final GlobalPermissionService globalPermissionService;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public StaffController(
            StaffService staffService,
            GlobalPermissionService globalPermissionService,
            UserRepository userRepository,
            TenantContext tenantContext) {
        this.staffService = staffService;
        this.globalPermissionService = globalPermissionService;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/permissions")
    public ResponseEntity<OwnGlobalPermissionsDto> ownPermissions() {
        if (tenantContext.isStaffAdmin()) {
            return ResponseEntity.ok(
                    new OwnGlobalPermissionsDto(
                            List.of(GlobalPermission.values()), tenantContext.isStaff()));
        }

        User user = currentUser();
        List<GlobalPermission> permissions =
                List.copyOf(globalPermissionService.effectivePermissions(user));

        return ResponseEntity.ok(new OwnGlobalPermissionsDto(permissions, tenantContext.isStaff()));
    }

    @PostMapping("/users")
    public ResponseEntity<StaffUserDetailDto> createStaffUser(
            @Valid @RequestBody CreateStaffUserRequestDto request) {
        User created =
                staffService.createStaffUser(request.email(), request.role(), request.profile());

        // A freshly created staff user always has zero direct/group permissions, so this is built
        // directly rather than via getStaffUserDetail — that method requires
        // STAFF_PERMISSION_MANAGE,
        // a *different* permission from the STAFF_USER_CREATE this endpoint itself requires, and
        // calling it here would wrongly demand both from a caller who only holds the latter.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new StaffUserDetailDto(
                                created.getId(),
                                created.getEmail(),
                                created.getGlobalRole(),
                                List.of(),
                                List.of(),
                                List.of(),
                                false));
    }

    @GetMapping("/users")
    public ResponseEntity<List<StaffUserSummaryDto>> listStaffUsers(
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(staffService.listStaffUsers(email));
    }

    @GetMapping("/users/{userId}/audit-trail")
    public ResponseEntity<PageResponseDto<AuditEventDto>> auditTrail(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(staffService.getAuditTrail(userId, page, size));
    }

    @GetMapping("/users/{userId}/permissions")
    public ResponseEntity<StaffUserDetailDto> staffUserDetail(@PathVariable Long userId) {
        return ResponseEntity.ok(staffService.getStaffUserDetail(userId));
    }

    @PostMapping("/users/{userId}/permissions")
    public ResponseEntity<Void> grantPermission(
            @PathVariable Long userId, @Valid @RequestBody GlobalPermissionRequestDto request) {
        staffService.grantPermission(userId, request.permission());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/permissions/{permission}/deletion-confirmation-token")
    public ResponseEntity<DeletionConfirmationTokenDto>
            generatePermissionRevocationDeletionConfirmationToken(
                    @PathVariable Long userId,
                    @PathVariable GlobalPermission permission,
                    @RequestHeader(value = "Accept-Language", required = false)
                            String acceptLanguage) {
        return ResponseEntity.ok(
                new DeletionConfirmationTokenDto(
                        staffService.generatePermissionRevocationDeletionConfirmationToken(
                                userId, permission, acceptLanguage)));
    }

    @DeleteMapping("/users/{userId}/permissions/{permission}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable Long userId,
            @PathVariable GlobalPermission permission,
            @RequestBody(required = false) DeleteConfirmationRequestDto request) {
        staffService.revokePermission(userId, permission, request == null ? null : request.word());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/demote")
    public ResponseEntity<Void> demoteStaffUser(@PathVariable Long userId) {
        staffService.demoteStaffUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/promote")
    public ResponseEntity<Void> promoteStaffUser(@PathVariable Long userId) {
        staffService.promoteStaffUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/deletion-confirmation-token")
    public ResponseEntity<DeletionConfirmationTokenDto> generateStaffUserDeletionConfirmationToken(
            @PathVariable Long userId,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return ResponseEntity.ok(
                new DeletionConfirmationTokenDto(
                        staffService.generateStaffUserDeletionConfirmationToken(
                                userId, acceptLanguage)));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteStaffUser(
            @PathVariable Long userId,
            @RequestBody(required = false) DeleteConfirmationRequestDto request) {
        staffService.deleteStaffUser(userId, request == null ? null : request.word());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/permissions/batch/deletion-confirmation-token")
    public ResponseEntity<DeletionConfirmationTokenDto>
            generateBatchPermissionUpdateDeletionConfirmationToken(
                    @PathVariable Long userId,
                    @RequestHeader(value = "Accept-Language", required = false)
                            String acceptLanguage) {
        return ResponseEntity.ok(
                new DeletionConfirmationTokenDto(
                        staffService.generateBatchPermissionUpdateDeletionConfirmationToken(
                                userId, acceptLanguage)));
    }

    @PutMapping("/users/{userId}/permissions/batch")
    public ResponseEntity<Void> batchUpdatePermissions(
            @PathVariable Long userId,
            @Valid @RequestBody BatchPermissionUpdateRequestDto request) {
        staffService.batchUpdatePermissions(userId, request.permissions(), request.word());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/access-groups")
    public ResponseEntity<List<GlobalAccessGroupDto>> listAccessGroups() {
        return ResponseEntity.ok(staffService.listAccessGroups());
    }

    @PostMapping("/access-groups")
    public ResponseEntity<GlobalAccessGroupDto> createAccessGroup(
            @Valid @RequestBody CreateGlobalAccessGroupRequestDto request) {
        return ResponseEntity.ok(
                GlobalAccessGroupDto.from(staffService.createAccessGroup(request.name())));
    }

    @PostMapping("/access-groups/{accessGroupId}/permissions")
    public ResponseEntity<Void> grantAccessGroupPermission(
            @PathVariable Long accessGroupId,
            @Valid @RequestBody GlobalPermissionRequestDto request) {
        staffService.grantAccessGroupPermission(accessGroupId, request.permission());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/access-groups/{accessGroupId}/permissions/{permission}")
    public ResponseEntity<Void> deleteAccessGroupPermission(
            @PathVariable Long accessGroupId, @PathVariable GlobalPermission permission) {
        staffService.revokeAccessGroupPermission(accessGroupId, permission);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/access-groups/{accessGroupId}")
    public ResponseEntity<Void> assignAccessGroup(
            @PathVariable Long userId, @PathVariable Long accessGroupId) {
        staffService.assignAccessGroup(userId, accessGroupId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/access-groups/{accessGroupId}/deletion-confirmation-token")
    public ResponseEntity<DeletionConfirmationTokenDto>
            generateAccessGroupUnassignmentDeletionConfirmationToken(
                    @PathVariable Long userId,
                    @PathVariable Long accessGroupId,
                    @RequestHeader(value = "Accept-Language", required = false)
                            String acceptLanguage) {
        return ResponseEntity.ok(
                new DeletionConfirmationTokenDto(
                        staffService.generateAccessGroupUnassignmentDeletionConfirmationToken(
                                userId, accessGroupId, acceptLanguage)));
    }

    @DeleteMapping("/users/{userId}/access-groups/{accessGroupId}")
    public ResponseEntity<Void> unassignAccessGroup(
            @PathVariable Long userId,
            @PathVariable Long accessGroupId,
            @RequestBody(required = false) DeleteConfirmationRequestDto request) {
        staffService.unassignAccessGroup(
                userId, accessGroupId, request == null ? null : request.word());
        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
