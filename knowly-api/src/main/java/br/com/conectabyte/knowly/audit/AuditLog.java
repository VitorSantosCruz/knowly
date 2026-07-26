package br.com.conectabyte.knowly.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Writes an AuditEvent for every call to the annotated method, success or failure, covering both
 * reads and writes (REQ-20) from one mechanism rather than a hand-written log call in every
 * handler.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    String action();

    String resourceType() default "";

    /** SpEL expression evaluated against the method's arguments, e.g. "#id". */
    String resourceIdExpression() default "";

    /**
     * Opt-in only: when {@code true}, the current request's source IP (masked via {@link
     * br.com.conectabyte.knowly.observability.PiiMasker#maskIp}) is recorded in {@code
     * metadata.sourceIp}. Defaults to {@code false} so this stays scoped to the handful of call
     * sites that need it (authentication events) rather than every {@code @AuditLog} consumer.
     */
    boolean captureSourceIp() default false;
}
