package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * permission-granularity-model REQ-1/REQ-2/REQ-3: {@link GlobalPermission#viewDependency()} is the
 * single authoritative source for which global permissions require a corresponding view/list
 * permission.
 */
class GlobalPermissionTest {

    @Test
    void everyExpectedPairIsWiredAndEverythingElseIsEmpty() {
        Map<GlobalPermission, GlobalPermission> expected = new EnumMap<>(GlobalPermission.class);
        expected.put(GlobalPermission.TENANT_EDIT, GlobalPermission.TENANT_VIEW);
        expected.put(GlobalPermission.TENANT_DELETE, GlobalPermission.TENANT_VIEW);
        expected.put(GlobalPermission.STAFF_USER_EDIT, GlobalPermission.STAFF_USER_VIEW);
        expected.put(GlobalPermission.STAFF_USER_DELETE, GlobalPermission.STAFF_USER_VIEW);
        expected.put(GlobalPermission.TENANT_MEMBER_EDIT, GlobalPermission.TENANT_MEMBER_VIEW);
        expected.put(GlobalPermission.TENANT_MEMBER_DELETE, GlobalPermission.TENANT_MEMBER_VIEW);
        expected.put(
                GlobalPermission.TENANT_ACCESS_GROUP_EDIT,
                GlobalPermission.TENANT_ACCESS_GROUP_VIEW);
        expected.put(
                GlobalPermission.TENANT_ACCESS_GROUP_DELETE,
                GlobalPermission.TENANT_ACCESS_GROUP_VIEW);
        expected.put(
                GlobalPermission.TENANT_PERMISSION_GRANT_DELETE,
                GlobalPermission.TENANT_PERMISSION_GRANT_VIEW);

        for (GlobalPermission permission : EnumSet.allOf(GlobalPermission.class)) {
            Optional<GlobalPermission> expectedDependency =
                    Optional.ofNullable(expected.get(permission));
            assertThat(permission.viewDependency())
                    .as("viewDependency() for %s", permission)
                    .isEqualTo(expectedDependency);
        }
    }
}
