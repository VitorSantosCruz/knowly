package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.dto.CreateGlobalAccessGroupRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.GlobalAccessGroupDto;
import br.com.conectabyte.knowly.tenancy.dto.GlobalPermissionRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.OwnGlobalPermissionsDto;
import br.com.conectabyte.knowly.tenancy.dto.StaffUserDetailDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
                    new OwnGlobalPermissionsDto(List.of(GlobalPermission.values())));
        }

        User user = currentUser();
        List<GlobalPermission> permissions =
                List.copyOf(globalPermissionService.effectivePermissions(user));

        return ResponseEntity.ok(new OwnGlobalPermissionsDto(permissions));
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

    @DeleteMapping("/users/{userId}/permissions/{permission}")
    public ResponseEntity<Void> revokePermission(
            @PathVariable Long userId, @PathVariable GlobalPermission permission) {
        staffService.revokePermission(userId, permission);
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

    @PostMapping("/users/{userId}/access-groups/{accessGroupId}")
    public ResponseEntity<Void> assignAccessGroup(
            @PathVariable Long userId, @PathVariable Long accessGroupId) {
        staffService.assignAccessGroup(userId, accessGroupId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}/access-groups/{accessGroupId}")
    public ResponseEntity<Void> unassignAccessGroup(
            @PathVariable Long userId, @PathVariable Long accessGroupId) {
        staffService.unassignAccessGroup(userId, accessGroupId);
        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
