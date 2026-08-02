package br.com.conectabyte.knowly.identity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.identity.ContactType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Bean Validation on {@link MandatoryProfileFieldsDto}/{@link MandatoryAddressDto} per
 * specify/features/mandatory-complete-profile/PLAN.md's "one shared mandatory profile fields DTO
 * shape" decision.
 */
class MandatoryProfileFieldsDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private MandatoryAddressDto completeAddress() {
        return new MandatoryAddressDto(
                "01000-000", "Rua Um", null, null, "Centro", "Sao Paulo", "SP", "Brasil");
    }

    private MandatoryProfileFieldsDto completeFields() {
        return new MandatoryProfileFieldsDto(
                "Jane Doe",
                LocalDate.of(1990, 1, 1),
                "12345678901",
                "123456",
                "SSP",
                completeAddress(),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    @Test
    void aFullyPopulatedPayloadHasNoViolations() {
        assertThat(validator.validate(completeFields())).isEmpty();
    }

    @Test
    void numeroAndComplementoMayBeOmittedOnTheAddress() {
        MandatoryAddressDto address =
                new MandatoryAddressDto(
                        "01000-000", "Rua Um", null, null, "Centro", "Sao Paulo", "SP", "Brasil");

        assertThat(validator.validate(address)).isEmpty();
    }

    @Test
    void missingFullNameIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        null,
                        LocalDate.of(1990, 1, 1),
                        "12345678901",
                        "123456",
                        "SSP",
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingBirthDateIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        null,
                        "12345678901",
                        "123456",
                        "SSP",
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingCpfIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        LocalDate.of(1990, 1, 1),
                        null,
                        "123456",
                        "SSP",
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingRgIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        LocalDate.of(1990, 1, 1),
                        "12345678901",
                        null,
                        "SSP",
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingRgOrgaoEmissorIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        LocalDate.of(1990, 1, 1),
                        "12345678901",
                        "123456",
                        null,
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingAddressIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        LocalDate.of(1990, 1, 1),
                        "12345678901",
                        "123456",
                        "SSP",
                        null,
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void emptyContactsIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        LocalDate.of(1990, 1, 1),
                        "12345678901",
                        "123456",
                        "SSP",
                        completeAddress(),
                        List.of());

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void addressMissingCepIsRejected() {
        MandatoryAddressDto address =
                new MandatoryAddressDto(
                        null, "Rua Um", null, null, "Centro", "Sao Paulo", "SP", "Brasil");

        Set<ConstraintViolation<MandatoryAddressDto>> violations = validator.validate(address);

        assertThat(violations).isNotEmpty();
    }
}
