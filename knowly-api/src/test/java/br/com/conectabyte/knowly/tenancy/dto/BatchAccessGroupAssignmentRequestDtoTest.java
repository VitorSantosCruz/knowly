package br.com.conectabyte.knowly.tenancy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * REQ-1/REQ-4, AppSec finding: bean-validation round trip for {@link
 * BatchAccessGroupAssignmentRequestDto}.
 */
class BatchAccessGroupAssignmentRequestDtoTest {

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

    @Test
    void rejectsANullAccessGroupIdsList() {
        Set<ConstraintViolation<BatchAccessGroupAssignmentRequestDto>> violations =
                validator.validate(new BatchAccessGroupAssignmentRequestDto(null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsAnEmptyAccessGroupIdsList() {
        Set<ConstraintViolation<BatchAccessGroupAssignmentRequestDto>> violations =
                validator.validate(new BatchAccessGroupAssignmentRequestDto(List.of()));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rejectsAListOf51Ids() {
        List<Long> ids = LongStream.rangeClosed(1, 51).boxed().toList();

        Set<ConstraintViolation<BatchAccessGroupAssignmentRequestDto>> violations =
                validator.validate(new BatchAccessGroupAssignmentRequestDto(ids));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void acceptsANonEmptyListOfAtMost50Ids() {
        List<Long> ids = LongStream.rangeClosed(1, 50).boxed().toList();

        Set<ConstraintViolation<BatchAccessGroupAssignmentRequestDto>> violations =
                validator.validate(new BatchAccessGroupAssignmentRequestDto(ids));

        assertThat(violations).isEmpty();
    }
}
