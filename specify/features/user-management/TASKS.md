# TASKS — User management

> Atomic, sequential, verifiable tasks derived from PLAN.md.
> Each "Implement" task ends with `npm run format` and a small
> Conventional Commit before moving on.

## 1. Active tenant awareness

- [ ] 1. Test: `ActiveTenantService.fetch()` calls
      `GET /api/tenants/memberships` and exposes the `active: true`
      entry's id/name (Red).
- [ ] 2. Implement `ActiveTenantService` (Green).

## 2. Member service

- [ ] 3. Test: `MemberService.list(tenantId)` calls
      `GET /api/tenants/{tenantId}/members` (Red).
- [ ] 4. Implement `MemberService.list` (Green).
- [ ] 5. Test: `MemberService.add(tenantId, email, role)` calls
      `POST /api/tenants/{tenantId}/members` (Red).
- [ ] 6. Implement `MemberService.add` (Green).
- [ ] 7. Test: `MemberService.remove(tenantId, membershipId)` calls
      `DELETE .../members/{membershipId}` (Red).
- [ ] 8. Implement `MemberService.remove` (Green).
- [ ] 9. Test: `MemberService.getDetail(tenantId, membershipId)` calls
      `GET .../members/{membershipId}` (Red).
- [ ] 10. Implement `MemberService.getDetail` (Green).
- [ ] 11. Test: `MemberService.grantPermission`/`revokePermission` call
       `POST`/`DELETE .../permissions[/{permission}]` (Red).
- [ ] 12. Implement both (Green).
- [ ] 13. Test: `MemberService.listAccessGroups`/`createAccessGroup` call
       `GET`/`POST .../access-groups` (Red).
- [ ] 14. Implement both (Green).
- [ ] 15. Test: `MemberService.assignAccessGroup`/`unassignAccessGroup`
       call `POST`/`DELETE .../access-groups/{id}` (Red).
- [ ] 16. Implement both (Green).

## 3. Members list screen (REQ-1, REQ-2, REQ-3, REQ-7)

- [ ] 17. Test: `MembersPageComponent` renders the member list on load
       (Red).
- [ ] 18. Implement `MembersPageComponent` + route `/members` (Green).
- [ ] 19. Test: submitting the add-member form calls `MemberService.add`
       and refreshes the list (Red).
- [ ] 20. Implement the add-member form (Green).
- [ ] 21. Test: clicking remove calls `MemberService.remove` and removes
       the row (Red).
- [ ] 22. Implement the remove action (Green).
- [ ] 23. Test: a 403 from any action renders
       `PermissionDeniedStateComponent` (Red).
- [ ] 24. Implement `PermissionDeniedStateComponent` + error handling
       (Green).

## 4. Member detail panel (REQ-4, REQ-5, REQ-6)

- [ ] 25. Test: selecting a member shows `MemberDetailPanelComponent`
       with direct permissions, access groups, and effective permissions
       as distinct sections (Red).
- [ ] 26. Implement `MemberDetailPanelComponent` (Green).
- [ ] 27. Test: toggling a permission calls grant/revoke and re-fetches
       the detail, updating the effective set shown (Red).
- [ ] 28. Implement the toggle wiring (Green).
- [ ] 29. Test: creating an access group makes it available to assign
       (Red).
- [ ] 30. Implement access-group creation (Green).
- [ ] 31. Test: assigning/unassigning an access group updates the
       member's access groups and effective permissions shown (Red).
- [ ] 32. Implement assign/unassign wiring (Green).

## 5. i18n and design

- [ ] 33. Add member-management i18n keys to `public/i18n/en.json` /
       `pt-BR.json`.
- [ ] 34. Apply the established design-system standard to the members
       list and detail panel.

## 6. Final verification

- [ ] 35. Run `npm run format:check && npm test && npm run build` and
       confirm everything is green.
- [ ] 36. Update `PLAN.md`'s "Emergent decisions" if anything changed.
- [ ] 37. Update `SPEC.md`'s acceptance-criteria checkboxes to reflect
       what's now verified by tests.
