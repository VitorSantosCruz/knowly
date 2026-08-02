package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REQ-7/REQ-8's Observability NFR: a Bean Validation failure on {@code StaffController
 * #createStaffUser}/{@code TenantController#addMember} happens before the target
 * {@code @AuditLog}-annotated service method is ever entered, so the existing per-method
 * {@code @AuditLog} mechanism can't record the denial -- this advice writes it directly, scoped (by
 * checking the exception's target handler method) to exactly those two call sites, per
 * specify/features/mandatory-complete-profile/PLAN.md. Every other {@code @Valid} validation
 * failure in the app still gets a plain 400 (this advice doesn't audit it, but the status code is
 * unchanged from before this feature existed).
 */
@RestControllerAdvice
public class CreationValidationAuditAdvice {

    private static final String STAFF_CONTROLLER_CLASS_NAME =
            "br.com.conectabyte.knowly.tenancy.StaffController";
    private static final String TENANT_CONTROLLER_CLASS_NAME =
            "br.com.conectabyte.knowly.tenancy.TenantController";

    private final AuditEventWriter auditEventWriter;
    private final UserRepository userRepository;

    public CreationValidationAuditAdvice(
            AuditEventWriter auditEventWriter, UserRepository userRepository) {
        this.auditEventWriter = auditEventWriter;
        this.userRepository = userRepository;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Void> handleValidationFailure(MethodArgumentNotValidException ex) {
        String action = resolveDeniedAction(ex);

        if (action != null) {
            audit(action, ex);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    private String resolveDeniedAction(MethodArgumentNotValidException ex) {
        MethodParameter parameter = ex.getParameter();
        Method method = parameter.getMethod();

        if (method == null) {
            return null;
        }

        String declaringClassName = method.getDeclaringClass().getName();

        if (STAFF_CONTROLLER_CLASS_NAME.equals(declaringClassName)
                && "createStaffUser".equals(method.getName())) {
            return "staff.user.creation.denied";
        }

        if (TENANT_CONTROLLER_CLASS_NAME.equals(declaringClassName)
                && "addMember".equals(method.getName())) {
            return "tenant.member.creation.denied";
        }

        if (TENANT_CONTROLLER_CLASS_NAME.equals(declaringClassName)
                && "createTenant".equals(method.getName())) {
            return "tenant.create.denied";
        }

        return null;
    }

    private void audit(String action, MethodArgumentNotValidException ex) {
        Long actorUserId = currentActorId();
        Set<String> missingFields = new LinkedHashSet<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            missingFields.add(fieldError.getField());
        }

        AuditEvent event =
                new AuditEvent(actorUserId, null, action, null, null, AuditOutcome.DENIED);
        event.setMetadata("{\"missingFields\":" + toJsonArray(missingFields) + "}");
        auditEventWriter.write(event);
    }

    private Long currentActorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return null;
        }

        return userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }

    private String toJsonArray(Set<String> values) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (String value : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(value.replace("\"", "\\\"")).append('"');
            first = false;
        }

        return sb.append(']').toString();
    }
}
