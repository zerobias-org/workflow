---
description: Create a new activity (and the workflow/field/role content it needs) in a workflow package
argument-hint: <package-path> <activity-code>
---

Create activity `$2` in `package/$1`.

## Repository scope rule

One package at a time. Do not edit files in sibling `package/*` directories.
Run zbb commands from inside the package directory.

## Phase 0 — Prerequisites

Run `/prerequisites`. Anything ❌ stops the flow (install or wait, never work around).

## Phase 1 — Look up what exists (reuse first)

Platform content is read live through the `zb` MCP, never copied into this repo:

| Need | Operation |
|---|---|
| Existing activities, their RACI, fields, `extends` chain | `platform.Activity.get` (uuid, `package.code` or bare code), `portal.Activity.search` (`packageCodes`) |
| Workflows and their transitions | `platform.Workflow.list`, then `platform.Workflow.get` (only `get` returns transitions) |
| Custom fields | `portal.CustomField.variableSearch` (`scopeId` = your org, no `activityId`) |
| Role codes | `platform.CatalogRole.list`, then `platform.CatalogRole.get` per role (only `get` returns `code`) |
| Statuses and phases | `portal.Task.listStatuses`, `portal.Task.listPhases` |

`zerobias_describe` each operation before its first call.

## Phase 2 — Decide

In this order, stop at the first that fits:
1. An existing activity already does it → don't create one.
2. An existing activity is close → `extends` it (child states only what differs; `$inherit` in `customFields` keeps the parent's bindings).
3. An existing workflow fits → new activity on that workflow.
4. Only then a new workflow.

## Phase 3 — Write

- New package: `./scripts/createNewWorkflow.sh package/<vendor>/<product>` (or `<vendor>/<suite>/<product>`).
- File `activity/$2.yml`; the filename is the code: `^[A-Za-z0-9_]+$`, at most 16 characters.
- `id`: a fresh v4 UUID, never reused or copied.
- References (`workflow`, `extends`, RACI roles, `customFields`, transition `customFields`):
  - Forms: bare `code`, qualified `<package>.<code>` (e.g. `zerobias.zerobias.platform.workflow.contextDev`), or uuid.
  - Bare resolves in this package, then `zerobias.imports` in order, then the platform packages
    (`zerobias.zerobias.platform.workflow`, `zerobias.zerobias.platform.rbac`, `nist.nice.role`, …);
    first match wins, so a code defined here shadows a platform one.
  - Qualified is exact, no fallback; its package must be this one, an import, or a platform package.
- Every loader directory you add (`workflow/`, `field/`, `role/<orgType>/`, …) goes in
  `package.json` `files`.

## Phase 4 — Gate

`/validate-workflow $1` — then commit `gate-stamp.json` with the change.

## Phase 5 — Review

Invoke the `activity-reviewer` agent on the package. Fix every blocking finding and re-run Phase 4.

## Phase 6 — Org load and PR

`zbb --slot <slot> publishOrg` → verify the activity in your org → explicit user sign-off →
PR against `dev`. No ZeroBias org: stop after Phase 5 and open the PR against `dev`.
