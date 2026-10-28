---
description: Run the quality gates for a workflow package
argument-hint: <package-path>
---

Validate `package/$1`. Run every command from inside the package directory.

## Gate 1 — Cheap pre-checks (seconds)

```bash
# Codes: filename = code, charset and length (activities ≤ 16)
find . -path ./node_modules -prune -o -name '*.yml' -print | sed 's|.*/||; s|\.yml$||' | grep -vE '^[A-Za-z0-9_]+$'
find activity -name '*.yml' 2>/dev/null | sed 's|.*/||; s|\.yml$||' | awk 'length > 16'
# Placeholders left from templates
grep -nE '\{[a-z]+\}' package.json
```

Each command should print nothing.

## Gate 2 — Validator + dataloader (`zbb --slot <slot> gate`)

- `validateContent`: package identity, non-empty, `files` covers every loader directory,
  every trigger has an `id`, repo-wide unique package names and ids.
- `testDataloader`: loads the package into a temporary copy of the platform content with the
  real loader (`DATALOADER_ENV` from `zbb.yaml`). Any loader error fails the gate.
- Writes `gate-stamp.json`. Commit it.

## Gate 3 — Review

`activity-reviewer` agent: what the loader accepts but is still wrong.
