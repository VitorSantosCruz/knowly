package br.com.conectabyte.knowly.audit;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
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
 * checking the exception's target handler method) to exactly those two call sites (plus {@code
 * TenantController#createTenant}), per specify/features/mandatory-complete-profile/PLAN.md.
 *
 * <p>This is also, incidentally, the only {@code @ExceptionHandler(MethodArgumentNotValidException
 * .class)} registered anywhere in the app, so it's dispatched for every {@code @Valid} failure on
 * every endpoint, not just the three it audits -- it always builds the same structured {@link
 * ValidationErrorResponseDto} body (matching the frontend's existing field-error parsing contract)
 * regardless of whether the failing handler method is one of the audited actions; only the audit
 * *logging* side effect stays scoped to those three call sites.
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
    public ResponseEntity<ValidationErrorResponseDto> handleValidationFailure(
            MethodArgumentNotValidException ex) {
        String action = resolveDeniedAction(ex);

        if (action != null) {
            audit(action, ex);
        }

        List<ValidationFieldErrorDto> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fieldError ->
                                        new ValidationFieldErrorDto(
                                                fieldError.getField(),
                                                fieldError.getDefaultMessage()))
                        .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ValidationErrorResponseDto(errors));
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
