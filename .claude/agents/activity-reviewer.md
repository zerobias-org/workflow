---
name: activity-reviewer
description: Reviews a workflow package for problems the loader accepts — reuse, references, traps, flags without effect
tools: Read, Grep, Glob, Bash, mcp__zb__zerobias_search, mcp__zb__zerobias_describe, mcp__zb__zerobias_execute
model: inherit
skills:
  - prerequisites
---

# Activity Reviewer

Review one package (`package/<path>`) after its gate passed. The gate proves the content
loads; this review catches what loads but is wrong. Report each finding as
**blocking** or **advisory**, with file and line.

Platform content is read live with the `zb` MCP, read operations only; never from a local
platform checkout, which may not match what is deployed.

## Blocking

- **Duplicates platform content.** An activity, workflow, field or role that already exists
  (look it up with the `zb` MCP operations in `/create-activity` Phase 1) instead of being
  reused or extended.
- **Renamed transition.** A new name is a new transition. Check `git diff` on `workflow/*.yml`
  for changed `name:` lines on existing transitions.
- **Copied or edited `id`.** Every new file has a fresh UUID; an existing file's `id` never changes.
- **Template field not bound.** Every `${code}` in `taskNameTemplate` /
  `taskDescriptionTemplate` is a task attribute or a field the activity binds; the gate does
  not catch it.
- **Transition requires an unbound field.** A field in a transition's `customFields` must be
  bound by the activity, or its tasks can never take that transition.

## Advisory

- **Ambiguous bare reference.** A bare code that exists in more than one package this one can see
  (own, imports, platform) binds the first match in that order. Confirm it is the intended one,
  or qualify it.
- For AI assistance, extend `aide` or `softwareDev`.
