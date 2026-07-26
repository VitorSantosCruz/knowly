-- MembershipRole.ADMIN renamed to MEMBER_ADMIN (see specify/features/role-model-refinement/SPEC.md
-- REQ-1..3) — distinct from GlobalRole.STAFF_ADMIN, never a schema/type change since `role` is
-- stored as a plain VARCHAR(20) via @Enumerated(EnumType.STRING), same precedent as V14.
UPDATE tenant_memberships SET role = 'MEMBER_ADMIN' WHERE role = 'ADMIN';
UPDATE tenant_memberships_aud SET role = 'MEMBER_ADMIN' WHERE role = 'ADMIN';
