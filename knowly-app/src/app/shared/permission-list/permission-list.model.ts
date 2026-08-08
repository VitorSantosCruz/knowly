// role-permission-management-ui: one row per permission value the caller wants rendered. `value`
// is the raw string enum value (`Permission`/`GlobalPermission` are both plain string-value
// enums), not a generic type parameter — see PLAN.md's "Architectural decisions" for why this
// component doesn't need one.
export interface PermissionListRow {
  readonly value: string;
  readonly granted: boolean;
}

export type PermissionListMode = 'editable' | 'readonly';
