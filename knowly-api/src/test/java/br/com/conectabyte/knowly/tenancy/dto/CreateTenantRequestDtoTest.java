package br.com.conectabyte.knowly.tenancy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.identity.ContactType;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryAddressDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** REQ-2/REQ-3/REQ-6: bean-validation round trip for {@link CreateTenantRequestDto}. */
class CreateTenantRequestDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static MandatoryProfileFieldsDto validProfile() {
        return new MandatoryProfileFieldsDto(
                "Test User",
                LocalDate.of(1990, 1, 1),
                "12345678901",
                "123456",
                "SSP",
                new MandatoryAddressDto(
                        "01000-000", "Rua Um", null, null, "Centro", "Sao Paulo", "SP", "Brasil"),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    private static AddressDto validAddress() {
        return new AddressDto("01000-000", "Rua Um", "1", null, "Centro", "Sao Paulo", "SP");
    }

    private static CreateTenantRequestDto request(
            String name,
            String legalName,
            String taxId,
            String country,
            String contactEmail,
            String contactPhone,
            AddressDto address,
            String adminEmail,
            MandatoryProfileFieldsDto profile) {
        return new CreateTenantRequestDto(
                name,
                legalName,
                taxId,
                country,
                contactEmail,
                contactPhone,
                address,
                adminEmail,
                profile,
                null);
    }

    private static CreateTenantRequestDto valid() {
        return request(
                "Acme",
                "Acme Ltda",
                "12345678000199",
                "BR",
                "contact@acme.com",
                "11999999999",
                validAddress(),
                "admin@acme.com",
                validProfile());
    }

    private void assertViolationOn(CreateTenantRequestDto invalid, String property) {
        Set<ConstraintViolation<CreateTenantRequestDto>> violations = validator.validate(invalid);

        assertThat(violations)
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo(property));
    }

    @Test
    void fullyValidInstanceHasZeroViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void missingNameTriggersAViolationOnName() {
        assertViolationOn(
                request(
                        "",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "name");
    }

    @Test
    void missingLegalNameTriggersAViolationOnLegalName() {
        assertViolationOn(
                request(
                        "Acme",
                        "",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "legalName");
    }

    @Test
    void missingTaxIdTriggersAViolationOnTaxId() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "taxId");
    }

    @Test
    void brazilWithWrongDigitCountTaxIdTriggersAViolationOnTaxId() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "123",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "taxId");
    }

    @Test
    void missingCountryTriggersAViolationOnCountry() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "country");
    }

    @Test
    void malformedContactEmailTriggersAViolationOnContactEmail() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "not-an-email",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "contactEmail");
    }

    @Test
    void missingContactPhoneTriggersAViolationOnContactPhone() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "",
                        validAddress(),
                        "admin@acme.com",
                        validProfile()),
                "contactPhone");
    }

    @Test
    void missingAddressTriggersAViolationOnAddress() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        null,
                        "admin@acme.com",
                        validProfile()),
                "address");
    }

    @Test
    void missingAddressPostalCodeTriggersANestedViolation() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        new AddressDto("", "Rua Um", "1", null, "Centro", "Sao Paulo", "SP"),
                        "admin@acme.com",
                        validProfile()),
                "address.postalCode");
    }

    @Test
    void missingAdminEmailTriggersAViolationOnAdminEmail() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "",
                        validProfile()),
                "adminEmail");
    }

    @Test
    void missingProfileTriggersAViolationOnProfile() {
        assertViolationOn(
                request(
                        "Acme",
                        "Acme Ltda",
                        "12345678000199",
                        "BR",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        null),
                "profile");
    }

    @Test
    void nonBrazilCountryWithAnyNonEmptyTaxIdIsValid() {
        CreateTenantRequestDto valid =
                request(
                        "Acme",
                        "Acme Ltda",
                        "EIN-1234",
                        "US",
                        "contact@acme.com",
                        "11999999999",
                        validAddress(),
                        "admin@acme.com",
                        validProfile());

        assertThat(validator.validate(valid)).isEmpty();
    }

    @Test
    void complementIsOptional() {
        // complement has no annotation at all -- AddressDto's constructor already allows null,
        // covered implicitly by every other passing test above using a null complement.
        assertThat(validAddress().complement()).isNull();
    }
}
