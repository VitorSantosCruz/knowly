package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {

    private final TenantContext tenantContext;
    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PermissionService permissionService;

    public PermissionAspect(
            TenantContext tenantContext,
            UserRepository userRepository,
            TenantMembershipRepository tenantMembershipRepository,
            PermissionService permissionService) {
        this.tenantContext = tenantContext;
        this.userRepository = userRepository;
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.permissionService = permissionService;
    }

    @Around("@annotation(br.com.conectabyte.knowly.audit.RequiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        if (tenantContext.isStaffAdmin()) {
            return joinPoint.proceed();
        }

        RequiresPermission requiresPermission =
                ((MethodSignature) joinPoint.getSignature())
                        .getMethod()
                        .getAnnotation(RequiresPermission.class);

        TenantMembership membership = requireActiveMembership();

        if (!permissionService.hasPermission(membership, requiresPermission.value())) {
            throw new PermissionDeniedException();
        }

        return joinPoint.proceed();
    }

    private TenantMembership requireActiveMembership() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(PermissionDeniedException::new);
        Long tenantId =
                tenantContext.getActiveTenantId().orElseThrow(PermissionDeniedException::new);

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        return tenantMembershipRepository
                .findByUserAndTenant(user, tenant)
                .filter(TenantMembership::isActive)
                .orElseThrow(PermissionDeniedException::new);
    }
}
