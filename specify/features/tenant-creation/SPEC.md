# SPEC — Tenant creation (staff)

> The what and the why. No technical implementation details.

## Context and motivation

The backend already supports creating a tenant (`POST /api/tenants`,
staff-only — see `knowly/specify/features/tenancy/SPEC.md` REQ-10), but
the frontend has no screen that calls it. A staff user currently has no
way, from the UI, to provision a new tenant and its first admin — the
only tenant-related screen today (`select-tenant-page`) lets a user
*choose* an existing tenant, not create one.

## User stories

- As a staff user, I want to create a new tenant and designate its
  first admin in one action, so that I can onboard a new client without
  needing direct API/database access.
- As a staff user with no tenant memberships, I want an obvious way to
  reach tenant creation from the screen I land on after login, so I'm
  not stuck with an empty tenant list and no next step.
- As a tenant user (non-staff), I want tenant creation to be invisible
  to me, since I have no way to use it and it isn't part of my role.

## Requirements (EARS/GEARS)

- **REQ-1 [Ubiquitous]** The system shall provide a dedicated route
  (`/tenants/new`) with a form to create a tenant, collecting the
  tenant name and the first admin's email.
- **REQ-2 [Ubiquitous]** Only staff users shall be able to navigate to
  or use the tenant creation route; this mirrors the backend's
  staff-only enforcement (REQ-10 of `tenancy`) but is enforced again on
  the frontend via a route guard, consistent with `tenant-selection.guard.ts`'s
  existing pattern of guarding tenant-context routes.
- **REQ-3 [Ubiquitous]** The `select-tenant-page` shall show a "create
  tenant" action, visible only to staff users, linking to `/tenants/new`.
- **REQ-4 [Event-Driven]** When a staff user submits the creation form
  with a valid name and admin email, the system shall call
  `POST /api/tenants` and, on success, navigate to the tenant selection
  page so the newly created tenant is selectable.
- **REQ-5 [Unwanted Behavior]** If the creation request fails (e.g.
  validation error, admin email already tied to a conflicting role, or
  a non-staff/forbidden response), then the system shall show an
  inline error and keep the user on the form with their input intact.
- **REQ-6 [Unwanted Behavior]** If a non-staff user reaches
  `/tenants/new` directly (e.g. via URL), then the system shall redirect
  them away, the same way `tenant-selection.guard.ts` redirects on other
  disallowed tenant-route access.

## Non-functional requirements

- Accessibility: form fields have associated labels, errors are
  announced via existing form-error conventions used elsewhere in the
  app (see `members-page` forms).
- Responsiveness: usable at mobile width, consistent with other
  narrow-form pages (e.g. `select-tenant-page`'s `max-w-md` pattern).

## Acceptance criteria

- [ ] A staff user can open `/tenants/new`, submit name + admin email,
      and lands back on tenant selection with the new tenant listed.
- [ ] A non-staff user cannot see the "create tenant" link and is
      redirected away if they navigate to `/tenants/new` directly.
- [ ] Submitting invalid/empty fields shows inline validation errors
      without calling the API.
- [ ] A backend error (e.g. 403, 409, 400) on submit shows an inline
      error message and preserves the entered values.

## Out of scope

- Any change to backend tenant-creation rules (staff-only stays
  staff-only; REQ-10 of `tenancy` is not being revisited).
- Editing or deleting existing tenants.
- Bulk/CSV tenant import.
