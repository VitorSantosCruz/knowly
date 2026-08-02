package br.com.conectabyte.knowly.tenancy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * REQ-6 (tenant-creation): {@code taxId} must reduce to exactly 14 digits when {@code country}
 * denotes Brazil, and be merely non-blank otherwise. Class-level (reads both {@code taxId} and
 * {@code country} off the annotated DTO), not a per-field constraint -- see
 * specify/features/tenant-creation/PLAN.md's "Open decision" section for why this is a deliberate,
 * documented divergence from this codebase's usual "no custom @Constraint" precedent.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TaxIdValidator.class)
public @interface ValidTaxId {

    String message() default "taxId is not valid for the given country";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
