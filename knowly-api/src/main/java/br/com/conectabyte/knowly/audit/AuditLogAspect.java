package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AccountLockedException;
import br.com.conectabyte.knowly.auth.exception.InvalidCredentialsException;
import br.com.conectabyte.knowly.observability.PiiMasker;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditLogAspect {

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private final AuditEventWriter auditEventWriter;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    public AuditLogAspect(
            AuditEventWriter auditEventWriter,
            UserRepository userRepository,
            TenantContext tenantContext) {
        this.auditEventWriter = auditEventWriter;
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
        } catch (PermissionDeniedException | TenantAccessDeniedException ex) {
            record(joinPoint, auditLog, AuditOutcome.DENIED);
            throw ex;
        } catch (InvalidCredentialsException ex) {
            record(joinPoint, auditLog, AuditOutcome.FAILURE);
            throw ex;
        } catch (AccountLockedException ex) {
            record(joinPoint, auditLog, AuditOutcome.LOCKED_OUT);
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
        event.setMetadata(resolveMetadata(joinPoint, auditLog));

        auditEventWriter.write(event);
    }

    private String resolveMetadata(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        String sourceIpEntry = null;
        if (auditLog.captureSourceIp()) {
            String sourceIp = resolveSourceIp();
            if (sourceIp != null) {
                String maskedIp = PiiMasker.maskIp(sourceIp);
                if (!maskedIp.isEmpty()) {
                    sourceIpEntry = "\"sourceIp\": \"" + maskedIp + "\"";
                }
            }
        }

        String roleEntry = null;
        if (!auditLog.metadataExpression().isEmpty()) {
            Object value = evaluateExpression(joinPoint, auditLog.metadataExpression());
            if (value != null) {
                roleEntry = "\"role\": \"" + value + "\"";
            }
        }

        if (sourceIpEntry == null && roleEntry == null) {
            return null;
        }

        StringBuilder json = new StringBuilder("{");
        if (sourceIpEntry != null) {
            json.append(sourceIpEntry);
        }
        if (roleEntry != null) {
            if (sourceIpEntry != null) {
                json.append(", ");
            }
            json.append(roleEntry);
        }
        json.append("}");

        return json.toString();
    }

    private String resolveSourceIp() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();

        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }

        return servletRequestAttributes.getRequest().getRemoteAddr();
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

        Object value = evaluateExpression(joinPoint, auditLog.resourceIdExpression());

        return value == null ? null : value.toString();
    }

    private Object evaluateExpression(ProceedingJoinPoint joinPoint, String spelExpression) {
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

        Expression expression = EXPRESSION_PARSER.parseExpression(spelExpression);
        return expression.getValue(context);
    }
}
