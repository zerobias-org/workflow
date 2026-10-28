# Contributing a workflow package

Anyone can author and validate a workflow package. There are two lanes, depending on whether
you have a ZeroBias platform account. AI-assisted contributors: `/create-activity` encodes both.

## Lane 1 — anyone with a GitHub account (no ZeroBias account needed)

1. **Tools**: git, Java 17+, Node 22, the `gh` CLI (and optionally `@zerobias-org/zbb`).
2. **GitHub token with `read:packages`** — required even though the packages are public
   (GitHub Packages Maven does not allow anonymous reads):

   ```bash
   gh auth refresh -s read:packages
   export GITHUB_TOKEN=$(gh auth token)
   ```

3. **Scaffold + author**:

   ```bash
   ./scripts/createNewWorkflow.sh package/<vendor>/<product>
   # add activity/<code>.yml, and any workflow/, field/, role/<orgType>/ files,
   # listing each directory in package.json "files"
   ```

4. **Validate**: `./gradlew :<vendor>:<product>:validateContent`. The full gate's dataloader
   step needs `ZB_TOKEN`; maintainers run it before merge.

5. **PR against `dev`.**

## Lane 2 — ZeroBias platform users (org-first delivery)

1. One-time credential setup + session launch:

   ```bash
   ./scripts/setup-org-credentials.sh --launch
   ```

2. In the session, run `/create-activity <package-path> <code>`: look up → decide → write →
   gate → review → `publishOrg` (org-private load) → you verify in your org → sign-off → PR
   against `dev`.

Notes for both lanes: commits follow Conventional Commits
(`feat(workflow-<vendor>-<product>): …`); never commit on `main` directly; never hand-edit
`version` after creation (CI owns bumps).
