package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.dto.SwitchActiveTenantRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantMembershipDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;
    private final PermissionService permissionService;
    private final UserRepository userRepository;

    public TenantController(
            TenantService tenantService,
            PermissionService permissionService,
            UserRepository userRepository) {
        this.tenantService = tenantService;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/memberships")
    public ResponseEntity<List<TenantMembershipDto>> listOwnMemberships() {
        User user = currentUser();
        List<TenantMembershipDto> memberships =
                tenantService.listOwnMemberships(user).stream()
                        .map(TenantMembershipDto::from)
                        .toList();

        return ResponseEntity.ok(memberships);
    }

    @PostMapping("/active")
    @AuditLog(
            action = "tenant.active_tenant.switch",
            resourceType = "Tenant",
            resourceIdExpression = "#request.tenantId()")
    public ResponseEntity<Void> switchActiveTenant(
            @Valid @RequestBody SwitchActiveTenantRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        User user = currentUser();
        TenantMembership membership =
                tenantService.requireActiveMembership(user, request.tenantId());

        List<GrantedAuthority> authorities =
                TenantAuthorityFactory.forMembership(
                        membership, permissionService.effectivePermissions(membership));

        var authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, httpRequest, httpResponse);

        HttpSession session = httpRequest.getSession(true);
        session.removeAttribute(TenantSessionKeys.SELECTION_PENDING);
        session.setAttribute(TenantSessionKeys.ACTIVE_TENANT_ID, request.tenantId());

        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
