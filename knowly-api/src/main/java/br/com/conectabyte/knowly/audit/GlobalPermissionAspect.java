package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermissionService;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class GlobalPermissionAspect {

    private final TenantContext tenantContext;
    private final UserRepository userRepository;
    private final GlobalPermissionService globalPermissionService;

    public GlobalPermissionAspect(
            TenantContext tenantContext,
            UserRepository userRepository,
            GlobalPermissionService globalPermissionService) {
        this.tenantContext = tenantContext;
        this.userRepository = userRepository;
        this.globalPermissionService = globalPermissionService;
    }

    @Around("@annotation(br.com.conectabyte.knowly.audit.RequiresGlobalPermission)")
    public Object checkGlobalPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        if (tenantContext.isStaffAdmin()) {
            return joinPoint.proceed();
        }

        RequiresGlobalPermission requiresGlobalPermission =
                ((MethodSignature) joinPoint.getSignature())
                        .getMethod()
                        .getAnnotation(RequiresGlobalPermission.class);

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(PermissionDeniedException::new);

        if (user.getGlobalRole() != GlobalRole.STAFF
                || !globalPermissionService.hasPermission(user, requiresGlobalPermission.value())) {
            throw new PermissionDeniedException();
        }

        return joinPoint.proceed();
    }
}
