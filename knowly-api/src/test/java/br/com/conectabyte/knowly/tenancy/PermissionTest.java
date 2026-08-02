package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * permission-granularity-model REQ-1/REQ-2/REQ-3: {@link Permission#viewDependency()} is the single
 * authoritative source for which permissions require a corresponding view/list permission.
 */
class PermissionTest {

    @Test
    void everyExpectedPairIsWiredAndEverythingElseIsEmpty() {
        Map<Permission, Permission> expected = new EnumMap<>(Permission.class);
        expected.put(Permission.ARTICLE_EDIT, Permission.ARTICLE_VIEW);
        expected.put(Permission.ARTICLE_DELETE, Permission.ARTICLE_VIEW);

        for (Permission permission : EnumSet.allOf(Permission.class)) {
            Optional<Permission> expectedDependency = Optional.ofNullable(expected.get(permission));
            assertThat(permission.viewDependency())
                    .as("viewDependency() for %s", permission)
                    .isEqualTo(expectedDependency);
        }
    }
}
