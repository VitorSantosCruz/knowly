package br.com.conectabyte.knowly.tenancy.validation;

import br.com.conectabyte.knowly.tenancy.dto.CreateTenantRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/** See {@link ValidTaxId}'s Javadoc for the rule this enforces. */
public class TaxIdValidator implements ConstraintValidator<ValidTaxId, CreateTenantRequestDto> {

    private static final Set<String> BRAZIL_LITERALS = Set.of("br", "brazil", "brasil");

    /**
     * Pure, Spring-free rule (REQ-6): package-private so {@code TaxIdValidatorTest} can exercise
     * every case directly without instantiating the full {@link CreateTenantRequestDto} or a
     * validator context.
     */
    static boolean isValid(String country, String taxId) {
        if (taxId == null || taxId.isBlank()) {
            return false;
        }

        if (country != null && BRAZIL_LITERALS.contains(country.trim().toLowerCase())) {
            String digitsOnly = taxId.replaceAll("\\D", "");
            return digitsOnly.length() == 14;
        }

        return true;
    }

    @Override
    public boolean isValid(CreateTenantRequestDto value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (isValid(value.country(), value.taxId())) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("taxId")
                .addConstraintViolation();

        return false;
    }
}
