import { GlobalPermission } from './global-permission';
import { Permission } from './permission';

// Re-exported string literals confirmed by grep against knowly-api's
// GlobalPermission/Permission enums (br.com.conectabyte.knowly.tenancy) — see
// specify/features/internal-team-chat/TASKS.md task 1. These two literals are not part
// of the pre-existing `ALL_GLOBAL_PERMISSIONS`/`ALL_PERMISSIONS` arrays in
// global-permission.ts/permission.ts (those two files are kept in sync with the *whole*
// backend enum elsewhere in the codebase); adding them there is out of scope for this
// feature, so they're declared as standalone typed constants here instead.
export const STAFF_SUPPORT_HANDLE = 'STAFF_SUPPORT_HANDLE' as GlobalPermission;
export const SUPPORT_CHANNEL_VIEW = 'SUPPORT_CHANNEL_VIEW' as Permission;
