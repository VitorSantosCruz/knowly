export type Permission =
  | 'TENANT_MEMBER_MANAGE'
  | 'ARTICLE_VIEW'
  | 'ARTICLE_CREATE'
  | 'ARTICLE_EDIT'
  | 'ARTICLE_DELETE'
  | 'CONVERSATION_USE'
  | 'DASHBOARD_VIEW'
  | 'PROFILE_VIEW'
  | 'PROFILE_EDIT'
  | 'SUPPORT_CHANNEL_VIEW';

// role-permission-management-ui: aligned with the backend `Permission` enum
// (`knowly-api/.../tenancy/Permission.java`) — `PROFILE_VIEW`/`SUPPORT_CHANNEL_VIEW` already
// existed at the backend and already had `permissions.<ENUM>` i18n labels, but were missing from
// this frontend union/array, so the new role-permission-editing views (which enumerate
// `ALL_PERMISSIONS`) could never show or toggle them. Not a new permission — just closing a
// pre-existing frontend gap.
export const ALL_PERMISSIONS: Permission[] = [
  'TENANT_MEMBER_MANAGE',
  'ARTICLE_VIEW',
  'ARTICLE_CREATE',
  'ARTICLE_EDIT',
  'ARTICLE_DELETE',
  'CONVERSATION_USE',
  'DASHBOARD_VIEW',
  'PROFILE_VIEW',
  'PROFILE_EDIT',
  'SUPPORT_CHANNEL_VIEW',
];
