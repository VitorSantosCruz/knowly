package br.com.conectabyte.knowly.identity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.identity.ContactType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Bean Validation on {@link MandatoryProfileFieldsDto}/{@link MandatoryAddressDto} per
 * specify/features/mandatory-complete-profile/PLAN.md's "one shared mandatory profile fields DTO
 * shape" decision. Updated 2026-08-02 for the country-agnostic identity/address model amendment:
 * {@code rg}/{@code rgOrgaoEmissor}/{@code birthDate} removed entirely; {@code cpf} renamed {@code
 * taxId}; {@code countryCode} added, required.
 */
class MandatoryProfileFieldsDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private MandatoryAddressDto completeAddress() {
        return new MandatoryAddressDto(
                "Rua Um, 100", "Centro", "Sao Paulo", "SP", "01000-000", "BR");
    }

    private MandatoryProfileFieldsDto completeFields() {
        return new MandatoryProfileFieldsDto(
                "Jane Doe",
                "52998224725",
                "BR",
                completeAddress(),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    @Test
    void aFullyPopulatedPayloadHasNoViolations() {
        assertThat(validator.validate(completeFields())).isEmpty();
    }

    @Test
    void addressLine2AndStateRegionMayBeOmittedOnTheAddress() {
        MandatoryAddressDto address =
                new MandatoryAddressDto("Rua Um, 100", null, "Sao Paulo", null, "01000-000", "BR");

        assertThat(validator.validate(address)).isEmpty();
    }

    @Test
    void missingFullNameIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        null,
                        "52998224725",
                        "BR",
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingTaxIdIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        null,
                        "BR",
                        completeAddress(),
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void missingCountryCodeIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe",
                        "52998224725",
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
                        "52998224725",
                        "BR",
                        null,
                        List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void emptyContactsIsRejected() {
        MandatoryProfileFieldsDto fields =
                new MandatoryProfileFieldsDto(
                        "Jane Doe", "52998224725", "BR", completeAddress(), List.of());

        assertThat(validator.validate(fields)).isNotEmpty();
    }

    @Test
    void addressMissingPostalCodeIsRejected() {
        MandatoryAddressDto address =
                new MandatoryAddressDto("Rua Um, 100", "Centro", "Sao Paulo", "SP", null, "BR");

        Set<ConstraintViolation<MandatoryAddressDto>> violations = validator.validate(address);

        assertThat(violations).isNotEmpty();
    }
}
