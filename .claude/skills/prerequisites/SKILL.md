---
name: prerequisites
description: >-
  Pre-flight for the workflow repo — verifies every hard requirement (tools,
  MCPs, credentials) before any content flow runs. USE THIS as step 0 of
  /create-activity, when the user asks "check prerequisites" / "am I set up",
  or when an orchestrated cross-repo flow includes this repo. Missing items
  get installed or waited for — never worked around.
---

# prerequisites — workflow repo pre-flight

Run EVERY check below and print a single report: `READY`, or the list of
missing items with their exact fix. Do not start any other work while
something is missing.

**The rule (universal, non-negotiable):** a missing prerequisite allows
exactly **two** actions — (1) install/configure it, asking the user first
where consent or credentials are needed, or (2) stop and wait. No
workarounds, no substitute tooling, no alternative approaches, no partial
continuation. A missing prerequisite PAUSES the flow; it never reroutes it.

**Checklist or nothing (hard):** the report is the FULL checklist — every
check row printed with its live status (✅/❌) — never a bare "READY".
`READY` exists only when every row is ✅ at the same moment. A single ❌ —
even in a component the next step doesn't touch (e.g. an MCP showing
disconnected/red in the UI) — means NOTHING moves: no "it'll recover on
its own", no continuing on the tools that still work, no deferring the
check "until we need it".

**Mutations invalidate the report:** any action that changes a
prerequisite component mid-flow (updating a CLI or MCP package, changing
slot env, switching profile) VOIDS the current checklist — re-run the
affected checks, re-print the full checklist, and resume only from
all-✅. Specifically: updating the npm package behind a LIVE stdio MCP
kills the running server (it goes red) — never hot-swap it mid-flow;
ask the user first, prefer deferring the update to the next launch, or
update + reconnect (`/mcp`) and verify with a live call (`meta.status`)
before anything else runs.

**Skill-vs-reality conflicts:** when a check's observed result contradicts
this skill's text, verify against the primary source (repo build files,
tool source, workflow YAML), act on the source, and fix this skill in the
same session — stale instructions are a prerequisite failure of their own.

## Checks

Two paths through this table:
- **Platform path** (default — you have a ZeroBias org): ALL rows are hard.
- **External contributor** (no ZeroBias account): rows 1–5 AND the GitHub-token row are hard; rows
  6–11 don't apply — follow the no-org path in `/create-activity` Phase 6 (gate → PR
  against `dev`; maintainers run the org verification). This is the ONE
  sanctioned scope reduction; everything else still follows
  install-or-wait.

| # | Requirement | Check | If missing |
|---|---|---|---|
| 1 | `git` + push access to this repo | `git push --dry-run origin HEAD` (a non-fast-forward rejection still proves auth) | fix remote/auth with the user |
| 2 | `gh` CLI authenticated | `gh auth status` | user runs `gh auth login` (interactive — theirs to do) |
| 3 | Java JDK 17+ (gradle gate) | `java -version` | install a JDK (e.g. `brew install temurin`) |
| 4 | Node + `npm` | `npm --version` | install Node LTS |
| 5 | `zbb` CLI | `command -v zbb && zbb --version` | `npm install -g @zerobias-org/zbb@latest` |
| 6 | **`zb` MCP connected** (`.mcp.json` + profile) | `mcp__zb__zerobias_*` tools in session | credentials setup below |
| 7 | **`zb-knowledge` MCP connected** (`.mcp.json` + launch env vars) | `mcp__zb-knowledge__*` tools in session; `health_check` succeeds | export `ZB_ORG_ID`/`ZB_API_KEY`, relaunch claude |
| 8 | `ZB_API_KEY` (**ORG key** — org OWNER, never member; legacy fallback `ZB_TOKEN` works on PROD targets ONLY — non-prod target + unset `ZB_API_KEY` = hard ❌, see ⚠ in Credentials) + `ZB_TOKEN` (**REGISTRY key**, prod-issued) + platform URLs in the slot env | `cd <repo> && [ -n "$(zbb --slot <slot> env get ZB_TOKEN 2>/dev/null \| tail -n1)" ] && echo present` — MUST run from inside the repo (any added-stack path): outside one, `env get` exits 1 with EMPTY output, indistinguishable from "unset" (false all-MISSING, hit 2026-08-18). Exit code alone is a FALSE POSITIVE the other way, and zbb may prefix a vault banner (value = last line) | credentials setup below |
| 9 | Target org UUID known | from user / org config | ask the user |
| 10 | Active `zb` profile org == target org, connection healthy | `zerobias_execute("meta.status")` → `healthy: true` + org matches target (CLI fallback: `zb status`) | `zb profile use <name>` / `zb setup`; org IDs are PER-ENVIRONMENT — a "No such Org" with working auth means wrong-env UUID, list memberships, never assume absence |
| 11 | Key is org OWNER (= Organization Admin) | `zerobias_execute("dana.Me.whoAmI", {})` → `isAdmin: true` | get an owner key — members cannot load artifacts |
| — | **GitHub token with `read:packages` — gates the ENTIRE zbb toolchain** (compile, validation, tests, `gate`, publish; the `zb.*` gradle plugins resolve from GitHub Packages Maven). `com.zerobias.build-tools` is PUBLIC — GHP Maven refuses ANONYMOUS reads, so this is a registry requirement, NOT a permission one: nothing needs granting, no org membership involved (verified 2026-08-31). ⚠ dev machines that ran `publishToMavenLocal` are silently exempt via `mavenLocal()`; clean/CI/container envs always need it — never generalise from a dev machine | **Check the SCOPE, not the login — being `gh` authenticated is NOT enough and is the usual false pass:** `gh auth status 2>&1 \| grep -qE '(read\|write):packages' && echo OK \|\| echo MISSING` (`write:packages` includes read). Definitive, and it decides when the two disagree (proves the read; 200 = ready, 401 = missing): `curl -s -o /dev/null -w '%{http_code}' -u "x:$(gh auth token)" https://maven.pkg.github.com/zerobias-org/util/zb/workspace/zb.workspace.gradle.plugin/maven-metadata.xml` | `gh auth refresh -s read:packages && export GITHUB_TOKEN=$(gh auth token)`, OR export a PAT carrying the scope. A 401 / `Plugin [id: 'zb.workspace'] was not found` / `Could not resolve com.zerobias.build-tools` is this row — KNOWN and SELF-FIXABLE: run the refresh and retry, never report it as an environment limitation, never fall back to `validateContent`-only, never write "validation deferred to CI". ⚠ an INVALID `GITHUB_TOKEN` env var silently shadows a valid keyring login — `gh auth status` exposes it |
| — | **build-tools plugin ≥ 1.0.137** (hard floor for org loads) | `./gradlew buildEnvironment \| grep build-tools` → ≥ 1.0.137 | usually a stale locally-published build-tools in `~/.m2` shadowing the release — remove `~/.m2/repository/com/zerobias/build-tools`; otherwise fix the GitHub-token row above |
| — | **`sem-apply`** (hard while `zbb.yaml` sets `DATALOADER_ENV=dev`: the gate applies the dev hydra schema to its Neon branch before loading) | `command -v sem-apply` | `gem install schema-evolution-manager -v 0.9.47` |
| — | Gate's Neon dataloader step | runs **iff `ZB_TOKEN` is present** (row 8 covers it; older `NEON_API_KEY` mentions are stale) | — |
| — | **`@zerobias-com/platform-dataloader` global** (hard — this repo's `zbb.yaml` lists it under `tools:` for `test` and `gate`: both exit 1 at preflight without it, printing an install hint that zbb does NOT run) | `command -v dataloader` + version ≥ 1.0.87 — read `$(npm root -g)/@zerobias-com/platform-dataloader/package.json` (`dataloader --version` boots the app and dials a DB first, ~5 s). Version only feeds zbb's preflight: the gate's Neon step runs its own `npx @zerobias-com/platform-dataloader@<env>`, so this tool is exempt from the freshness rule below | `./scripts/setup-org-credentials.sh` installs it when missing and reports when it is behind (re-run is safe, check-first); by hand: `zbb --slot <slot> --stack dev exec npm i -g @zerobias-com/platform-dataloader@latest` — needs the slot's `ZB_TOKEN`, a bare `npm i -g` 401s |

**Tooling freshness (hard).** The ZeroBias CLIs move fast and version skew
fails in confusing ways. For every UNPINNED @zerobias tool —
`@zerobias-org/zbb` and `@zerobias-com/zerobias-mcp` (`zb`) — the installed version
MUST equal the registry's latest: compare `npm view <pkg>@latest version` against
the installed one. Behind → update with consent (`zbb --slot <slot> --stack dev exec npm i -g <pkg>@latest`)
or stop and wait — never continue on stale tooling. Spell `@latest` in the
`npm view`: inside a slot the dev stack exports `NPM_CONFIG_TAG` (content-package
dist-tag routing), and a bare `npm view <pkg> version` resolves THAT tag — the
CLIs carry none, so the lookup comes back empty and must not read as "latest". Documented pins beat
freshness: never bump a pinned version to satisfy this rule.
⚠ Run the `@zerobias-com/*` `npm view` freshness checks from `$HOME`, not
the repo cwd — this repo's project `.npmrc` reroutes that scope to GitHub
Packages (SAML 403), and for non-global commands project config beats
`~/.npmrc`. `npm i -g` is the opposite: npm ≥ 7 IGNORES the project
`.npmrc` in global mode and reads only `~/.npmrc` (verified 2026-09-08),
whose `pkg.zerobias.org` scopes need `ZB_TOKEN` in the environment — in a
plain shell the token is not exported (it lives in the slot), so install
through the slot (`zbb --slot <slot> --stack dev exec npm i -g <pkg>@latest`)
or pass it inline (`ZB_TOKEN=<registry-key> npm i -g <pkg>@latest`). Always
use the full scoped package name (`@zerobias-org/zbb`, not `zbb` — none of
these packages exist on public npm). Full rule and the list of global CLIs:
[meta-repo `docs/RegistrySetup.md`](https://github.com/zerobias-org/zerobias/blob/main/docs/RegistrySetup.md#global-cli-installs).

**Slot naming (consistent, derived — never invent).** One slot per target
org/env, canonically named `<env>-<org first 8>` (env = platform-host
first label, `app` → `prod`; e.g. `ci-74fc0422`). Deterministic, so every
session derives the same name and REUSES the slot. Before creating one,
adopt any existing slot that already holds the same `ZB_PLATFORM_URL` +
`ZB_ORG_ID`. Never default to ad-hoc names like `local` — the
setup-org-credentials script implements this resolution automatically.

All builds/gates/publishes run **via `zbb`** (`zbb --slot <slot> gate` /
`publishOrg`) — never bare `./gradlew`; only zbb injects the slot env.
Workflow packages are nested projects: the gradle path is `:<vendor>:<product>`
or `:<vendor>:<suite>:<product>` — but with zbb you just run from the
package directory.

## Credentials — TWO keys, THREE homes (all hard-required)

Two keys with distinct jobs, plus **org ID** and **platform URL**:

- **`ZB_API_KEY` — the ORG key**: org-owner API key of the TARGET env.
  Consumers: the MCP logins (zb profile, zb-knowledge headers), the
  `/dana/me` owner check, and publishOrg's platform calls (build-tools
  falls back to `ZB_TOKEN` when unset — single-key setups keep working).
- **`ZB_TOKEN` — the REGISTRY key**: auths `pkg.zerobias.org` (npm reads,
  publishes, `.npmrc` interpolation) and the gate's Neon step. While the
  registry accepts only PROD keys, this must be a prod-issued key. Rule:
  don't touch it if it works — the setup script verifies registry access
  and prompts only on failure.

> ⚠ **Any target env other than `app.zerobias.com` REQUIRES `ZB_API_KEY`.**
> `ZB_TOKEN` must be prod-issued (registry requirement) and keys are
> per-environment, so when `ZB_PLATFORM_URL` is not
> `https://app.zerobias.com/api`, the single-key fallback 401s on the
> target env's `/dana/me` — always, not sometimes. Pre-flight rule:
> target ≠ prod AND `ZB_API_KEY` unset in the slot = hard ❌ on row 8;
> set the target-env org-owner key BEFORE starting, don't discover it at
> `verifyOrgPublish`. On prod targets the fallback works — and setting
> `ZB_API_KEY` to the same value as `ZB_TOKEN` is harmless there — so the
> simple habit is: always set both keys explicitly.

These must be installed in three places, because each consumer reads a
different store. The values come from the user, generated in the **TARGET
environment's** app UI (Settings → API Keys — app.zerobias.com for prod,
ci.zerobias.com for ci, …). **Keys are per-environment: a key from one env
returns 401 on another.** Ask; never invent them.

⚠️ **The ORG key (`ZB_API_KEY`, legacy fallback `ZB_TOKEN`) MUST be an org OWNER key — never a plain member key.** A
member key authenticates fine but cannot load artifacts into the org, so
every flow dies at the org-load step with a confusing late failure.
"Owner" in platform terms = **Organization Admin** (the org's admin
group). Verify up front with check #11: `dana.Me.whoAmI` → `isAdmin` must
be `true` — this is the exact `isOrgAdmin` gate the dataloader's
`queueJob` runs, and the same check `publishOrg`'s own
`VerifyOrgPublishTask` enforces at build time. (If `isAdmin` is absent
from the response, the deployed dana predates the field — fall back to
`dana.Org.getRequestOrgMember` and assert `admin: true`.)

**Preferred path — enter once, fan out:** have the user run
[`./scripts/setup-org-credentials.sh`](../../../scripts/setup-org-credentials.sh)
from the repo root in their own terminal (every content repo carries an
identical copy in its `scripts/`). It is **check-first**: already configured → prints the active slot /
platform / org and exits (re-run with `--reconfigure` to switch org, env,
or key); otherwise it prompts for each missing value once (API key
hidden, with a masked `abc…xyz` echo-back; env vars skip prompts; the org
can be given by NAME — it resolves to the UUID via /dana/me/orgs; re-runs
default to the last-used target, Enter to keep) and fixes only what's
missing — keeping the key out of the Claude session entirely. The
per-home reference:

1. **Repo-shipped `.mcp.json` + launch-time env vars** — declares BOTH
   MCPs with `${ZB_ORG_ID}` / `${ZB_API_KEY}` placeholders (no secrets
   committed; project-scoped by nature). The user **exports the vars in
   the shell that launches claude** (`--launch` does this automatically) — temporary, they die with the shell.
   Unset vars → the server shows a missing-var warning and stays
   unconnected (check #7 catches it). Headless `claude -p` loads
   `.mcp.json` without prompting; interactive first use shows a one-time
   trust prompt.
2. **`zb` MCP profile** (`~/.config/mcp-zb/credentials.json`, user-global —
   project-scoped zb creds is a pending platform ask) — consumer: platform
   ops. Install: ensure `~/.npmrc` has the `pkg.zerobias.org` scopes, then
   `npm install -g @zerobias-com/zerobias-mcp`, then `zb setup`
   (interactive prompts: URL, org ID, API key — in that order). Verify: `zb status` and
   that its org matches the target org.
3. **zbb slot env** (slot-scoped by construction) — consumer: the gradle
   gate + `publishOrg`. `zbb --slot <slot> env set ZB_TOKEN <token>`, plus
   `ZB_PLATFORM_URL=https://<env>/api` and `NPM_CONFIG_TAG=dev`.
   **Do NOT set `DATALOADER_SERVICE_URL`** — the gate's Neon step
   deliberately runs against its prod default
   (`https://app.zerobias.com/api/dataloader`) authed by the prod-issued
   `ZB_TOKEN`, even when the org target is another env; a per-env override
   makes the prod key 401 there. The org load never reads this var (it
   uses `ZB_PLATFORM_URL` + `ZB_API_KEY`).
   ⚠ **Slot-env mutation gate:** once a flow is running, any `env set`
   that redirects traffic or identity (URLs, `ZB_ORG_ID`, keys) requires
   showing the user the evidence and the exact command, and getting
   confirmation BEFORE running it — even when source code proves the
   change correct. Never silently repoint an environment.

Never register these MCPs with `claude mcp add -s project` — that would
write literal credentials into a committed file. The placeholder
`.mcp.json` is the committed form.

**Secrets hygiene.** The user may set all three homes themselves in a
separate terminal so the API key never enters the session — offer this
option. Either way: verification must never print secret values into the
session — use presence checks (`[ -n "$(… \| tail -n1)" ]` — never bare exit codes), `zb status`
(prints user/org, not the key), and MCP tool availability. Never read
`~/.claude.json` or `~/.config/mcp-zb/credentials.json` contents.
Secret VALUES never enter the session at all — no `vault kv get` of
secrets, no env dumps, not even "just to compare": hand the user a
ready-made command for their OWN terminal (compare hashes there if
needed) and consume only its pass/fail or hash output.

⚠️ MCP changes are picked up at launch: after the profile setup, or after
exporting `ZB_ORG_ID`/`ZB_TOKEN`, relaunch Claude Code from that shell
before re-running this pre-flight. Easiest: skip the manual exports and
re-run the script with `--launch` — it execs `claude` from the repo root
with all three values exported (read back from the slot). Anything after
`--launch` is passed to claude, so `--launch -p "/create-activity <package-path> <code>"` starts a
headless run the same way. Full reference:
<https://github.com/zerobias-org/zerobias/blob/main/docs/MCPs.md>

Repo-shipped `.claude/settings.json` pre-authorizes the flow's `zbb`
commands (gate / env get / publishOrg). If the harness still prompts or a
classifier denies one, have the user add the rule via `/permissions` —
`env set` (key installs) stays approval-gated on purpose.

## Output

Always print the FULL checklist — one row per check above with its live
status — in BOTH outcomes; a bare verdict without the table is invalid:

- All ✅ → the checklist, then `READY — all workflow-repo prerequisites
  satisfied.`
- Anything ❌ → the checklist, plus for each ❌: requirement · why it's
  needed · exact fix. Then only the two permitted actions until green;
  no other work starts (or resumes) before a re-printed all-✅ checklist.

## Consistency note

Every zerobias-org content repo carries this same skill under the same name,
tailored to its own requirements — deliberate duplication, kept in sync.
Cross-repo orchestrated flows MUST invoke every involved repo's
`prerequisites` skill up front, so every gap is caught before any work starts.
