# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

Monorepo of ZeroBias workflow artifacts: activities, workflows, custom fields and roles. Each
`package/<vendor>/<product>/` or `package/<vendor>/<suite>/<product>/` directory is one
publishable package, loaded by the platform dataloader as `import-artifact: workflow`.

Same gradle + [zbb publish reusable workflow](https://github.com/zerobias-org/devops/blob/main/.github/workflows/zbb-publish-reusable.yml)
pipeline as `org/product`, `org/suite` and `org/vendor`.

## Read first

A request to create or change an activity → run `/create-activity`; to validate a package →
`/validate-workflow`.

- [`/create-activity`](.claude/commands/create-activity.md) — the authoring flow: look up, decide, write, gate, review
- [`/validate-workflow`](.claude/commands/validate-workflow.md) — the gates
- [`activity-reviewer`](.claude/agents/activity-reviewer.md) — what the loader accepts but is still wrong
- [`prerequisites`](.claude/skills/prerequisites/SKILL.md) — tools, MCPs, credentials; install or wait, never work around

## Hard rules

- **Look it up, don't copy it.** Platform workflows, fields and roles are referenced, never
  duplicated here. Read them live with the `zb` MCP (`/create-activity` Phase 1).
- **Code = filename**: `^[A-Za-z0-9_]+$`; activity codes at most 16 characters.
- **`id` = a fresh v4 UUID per file**; never copied, never changed once published.
- **Never rename a transition** that has shipped: a new name is a new transition.
- **References** are bare, qualified or uuid; how each resolves: `/create-activity` Phase 3.
- **Every trigger has an `id`** (the validator enforces it).
- **Every loader directory you add goes in `package.json` `files`** (the validator enforces it).

## Development Commands

```bash
./gradlew :<vendor>:<product>:validateContent   # file-shape validation only
zbb --slot <slot> gate                          # from the package dir: validate → dataloader → gate-stamp.json
./gradlew validateUniquePackages                # repo-wide: unique zerobias.package and ids
./gradlew projectPaths                          # zbb CLI helpers
./gradlew changedModules
```

`gate` writes `package/<path>/gate-stamp.json`; commit it. `zbb.yaml` sets `DATALOADER_ENV=dev`
until the platform's package-code release (qualified `package.code` references) reaches prod; a
dev stamp publishes to `dev` only.

## Package Structure

| Path | npm name | `zerobias.package` |
|---|---|---|
| `package/<v>/<p>/` | `@zerobias-org/workflow-<v>-<p>` | `<v>.<p>.workflow` |
| `package/<v>/<s>/<p>/` | `@zerobias-org/workflow-<v>-<s>-<p>` | `<v>.<s>.<p>.workflow` |

Per package: `package.json` (`zerobias.import-artifact: workflow`, `package`, `imports`),
`.npmrc` (byte-identical copy of the root one), `build.gradle.kts`
(`plugins { id("zb.content") }`), and any of the loader directories `skill/ status/ field/
role/<orgType>/ workflow/ activity/ triggers/`. Scaffold with `./scripts/createNewWorkflow.sh`.

## Validator philosophy

The dataloader is the source of truth for content rules. The gate validator only enforces what
the dataloader CANNOT or DOES NOT see — see the comment at the top of `build.gradle.kts`. Don't
add validations the dataloader does better.

## Branches

- `dev` — **all package PRs target it**; bottom of the promotion chain `dev → qa → uat → main`.
- Non-package work (skills, docs, scripts, `zbb.yaml`, workflows) may PR straight to `main`.

## Commit format

[Conventional Commits](https://www.conventionalcommits.org/), enforced by `commitlint`.
Scopes: `workflow-<v>-<p>`, `bundle`, `validator`, `repo`.

## Bundle

`bundle/` ships `@zerobias-org/workflow-bundle`, listing every package as a dependency.
Auto-refreshed by the publish workflow's `update-bundle` job.

---

## Sessions, credentials & MCPs — slot-first

<!-- Synced section: identical in vendor, suite, product, module.
     The zerobias meta-repo's CLAUDE.md carries the same rules in its
     own words. Edit in one repo, copy to all. -->

All org credentials (platform ORG key, registry key, org/env identity)
live in a **zbb slot**; Claude Code sessions are launched THROUGH the
slot so the committed `.mcp.json` templates (`${VAR}` refs — no
secrets) and the zb `env` profile resolve that identity.

- **One-time setup (per org/env):** the user runs
  `./scripts/setup-org-credentials.sh` themselves in a normal terminal
  (never inside a Claude session). Check-first and re-runnable: it
  creates the slot (`<env>-<org-prefix>`), stores the keys, and wires
  `~/.npmrc` + the zb profile.
- **Launch:** `./scripts/setup-org-credentials.sh --launch [args…]`,
  or `zbb --slot <slot> --stack <stack> exec claude` from anywhere
  (`<stack>` = this repo's `zbb.yaml` `name:` short form, e.g.
  `vendor` in the vendor repo); from this repo's root plain
  `zbb --slot <slot> exec claude` works too (cwd infers the stack).
  NEVER launch stackless from outside a `zbb.yaml` directory: a slot
  holds NO user vars of its own (only `ZB_SLOT*` identity) — every
  credential is **stack-scoped**, and lives ONCE per slot on the
  shared `dev` stack (`@zerobias-org/dev-stack`); this repo's stack
  imports it (see `zbb.yaml` depends/imports), so the setup script
  seeds only the dev stack and every content stack resolves the same
  creds transitively. Never `env set` those vars on a content stack —
  a per-stack override shadows the import and rotation stops
  propagating there. `zbb --slot <slot> --stack dev exec claude`
  launches a creds-only session from anywhere (MCPs work; repo gates
  still need the repo's own stack). Add `--continue` to resume the
  previous session under another slot (sessions are keyed by cwd,
  not by slot).
- **Missing MCP tools / 401 / `MISSING_ENV_VAR` / `NOT SET`** means
  the session wasn't launched through a slot WITH a stack context.
  Check inside the session: `echo ${ZB_SLOT:-no-slot} ${ZB_ORG_ID:-no-stack}`
  (`no-slot` = not launched through zbb; `no-stack` = launched
  stackless). Fix the launch — exit and relaunch; `/mcp` reconnect can
  never pick up new env (it is captured once at claude startup). Do NOT
  register MCPs with pasted literal keys (a baked key silently
  overrides every slot identity, connecting as the wrong org) and do
  NOT export creds into the session as a workaround.
- **Multi-org / multi-env = one slot each**, chosen at launch time;
  switching identity means restarting claude through the other slot
  (env is read once at startup). A second IDENTITY (another API key)
  for the same org gets its own named slot too — a preset `SLOT` skips
  the reuse-by-content scan:
  `SLOT=<name> ZB_API_KEY=<other-key> ./scripts/setup-org-credentials.sh`.
  With several slots holding one org, always pass `--slot` explicitly —
  the auto-reuse scan just takes the first match.

Deep dive: the meta-repo's
[docs/MCPs.md](https://github.com/zerobias-org/zerobias/blob/main/docs/MCPs.md).

## Windows — WSL2 only

Everything here runs only on Ubuntu (`zbb` fails on native Windows).
On Windows, work inside WSL2 end-to-end — user walkthrough:
[docs/WindowsWSLSetup.md](https://github.com/zerobias-org/zerobias/blob/main/docs/WindowsWSLSetup.md).

- **If this session runs on NATIVE Windows** (prompt `PS C:\`, paths
  under `C:\` or `/mnt/c/...`): your ONLY job is getting WSL2 + Ubuntu
  installed. Refuse repo work — no cloning, editing, git, or builds —
  and point the user to their WSL session. Never relay work between a
  Windows agent and a WSL agent.
- **In WSL:** logins and credential setup happen in the Ubuntu
  terminal (`gh auth login`, claude's first-run login,
  `setup-org-credentials.sh`). Once setup is green, offer Remote
  Control (`/remote-control`, or `--launch --remote-control`) to
  continue from the Claude desktop / mobile app.
