package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLogAspect {

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public AuditLogAspect(
            AuditEventRepository auditEventRepository,
            UserRepository userRepository,
            TenantContext tenantContext) {
        this.auditEventRepository = auditEventRepository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
    }

    @Around("@annotation(br.com.conectabyte.knowly.audit.AuditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint) throws Throwable {
        AuditLog auditLog =
                ((MethodSignature) joinPoint.getSignature())
                        .getMethod()
                        .getAnnotation(AuditLog.class);

        try {
            Object result = joinPoint.proceed();
            record(joinPoint, auditLog, AuditOutcome.SUCCESS);
            return result;
        } catch (PermissionDeniedException ex) {
            record(joinPoint, auditLog, AuditOutcome.DENIED);
            throw ex;
        } catch (Throwable ex) {
            record(joinPoint, auditLog, AuditOutcome.ERROR);
            throw ex;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, AuditLog auditLog, AuditOutcome outcome) {
        Long actorUserId = resolveActorUserId();
        Long tenantId = tenantContext.getActiveTenantId().orElse(null);
        String resourceId = resolveResourceId(joinPoint, auditLog);

        AuditEvent event =
                new AuditEvent(
                        actorUserId,
                        tenantId,
                        auditLog.action(),
                        auditLog.resourceType().isEmpty() ? null : auditLog.resourceType(),
                        resourceId,
                        outcome);

        auditEventRepository.save(event);
    }

    private Long resolveActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private String resolveResourceId(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        if (auditLog.resourceIdExpression().isEmpty()) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames =
                PARAMETER_NAME_DISCOVERER.getParameterNames(signature.getMethod());
        EvaluationContext context = new StandardEvaluationContext();

        if (parameterNames != null) {
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        Expression expression = EXPRESSION_PARSER.parseExpression(auditLog.resourceIdExpression());
        Object value = expression.getValue(context);

        return value == null ? null : value.toString();
    }
}
