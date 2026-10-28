# Workflow monorepo

ZeroBias workflow artifacts: activities (task templates), workflows (status machines), and the
custom fields and roles they use. Each `package/<vendor>/<product>/` (or
`package/<vendor>/<suite>/<product>/`) directory is one publishable workflow package, loaded by
the platform dataloader as `import-artifact: workflow`.

Platform content (the built-in workflows, fields and roles) is not copied here: packages
reference it, and authors look it up live through the `zb` MCP.

## Authentication

Two credentials; setup steps in [CONTRIBUTING.md](CONTRIBUTING.md):

| Credential | What it unlocks | Needed by |
|---|---|---|
| `GITHUB_TOKEN` with **`read:packages`** | the `zb.*` gradle plugins from GitHub Packages Maven | everyone, every zbb/gradle command |
| `ZB_TOKEN` | the npm registry + the gate's dataloader step | package installs; gate; org loads |

## Create an activity

With Claude Code: `/create-activity <package-path> <code>`. By hand:

```bash
./scripts/createNewWorkflow.sh package/<vendor>/<product>   # new package
# add activity/<code>.yml (see package/zerobias/zerobias/base for an example)
cd package/<vendor>/<product> && zbb --slot <slot> gate      # writes gate-stamp.json
```

Commit `gate-stamp.json` with the change and open the PR against `dev`.

## Build & validate

```bash
./gradlew :<vendor>:<product>:validateContent   # file-shape checks only
zbb --slot <slot> gate                          # validate → dataloader load → gate-stamp.json
```

The gate loads the package with the real dataloader into a temporary copy of the platform
content. `zbb.yaml` sets `DATALOADER_ENV=dev` until the platform's package-code release
reaches prod; a dev stamp publishes to `dev` only.

## Validator philosophy

The dataloader is the source of truth for content rules. The validator (`build.gradle.kts`) only
enforces what the dataloader cannot see: package identity (path ↔ npm name ↔
`zerobias.package`), non-empty packages, `files` covering every loader directory, trigger ids,
and repo-wide unique package names and ids.

## Publishing, branches, commits

As in the other content repos: `.github/workflows/publish.yml` wraps the zbb publish reusable
workflow; package PRs target `dev` (`dev → qa → uat → main`); Conventional Commits enforced by
`commitlint`, scopes `workflow-<vendor>-<product>`, `bundle`, `validator`.
