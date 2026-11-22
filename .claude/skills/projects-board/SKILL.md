---
name: projects-board
description: Keeping the GitHub Projects v2 board "pokeplan-reboot" in step with the backlog: adding an item, setting Tier, State, Effort, Module and Kind, moving Status, and why `gh project` does not work for this board. Use whenever a backlog row is added, claimed, partly built, shipped or dropped, or when a gap in the docs is found.
---

# The Projects board

**Board:** <https://github.com/users/sophia-vista/projects/2> — owner `sophia-vista` (a User, not an organisation), number `2`, title `pokeplan-reboot`.

It mirrors `docs/pokelife/IMPLEMENTATION_BACKLOG.md`: one item per open REP row, plus items for work the docs require but no row tracks, plus the owner's open decisions. The board is a *view* of the backlog, never a second source of truth — the doc wins, and a row that changes there changes here.

## Why GraphQL, not `gh project`

`gh project list --owner sophia-vista` fails with **"unknown owner type"** even with the `project` scope, because the CLI cannot resolve a User-owned project. Every call below goes through `gh api graphql`. Do not try to fix the CLI path.

## Resolve the project id once

```bash
gh api graphql -f query='{ viewer { projectV2(number: 2) { id title } } }' \
  --jq '.data.viewer.projectV2.id, .data.viewer.projectV2.title'
```

It returns an opaque `PVT_…` id. Resolve it at the start of a run; do not hardcode it in scripts that outlive the session.

## The fields and their vocabulary

| Field | Built-in? | Values |
|---|---|---|
| **Status** | yes | `Todo`, `In Progress`, `Done` |
| **Tier** | project field | `1`, `2`, `3`, `4`, `5`, `Parked`, `Decision`, `Unsorted` |
| **State** | project field | `Planned`, `Partial`, `Needs owner`, `Needs device`, `Built (unverified)`, `Parked`, `Done` |
| **Effort** | project field | `S`, `M`, `L`, `Unknown` |
| **Module** | project field | `plan`, `care`, `heal`, `mint`, `base`, `link`, `gear`, `world`, `launcher`, `data`, `builds`, `core`, `ui`, `docs` |
| **Kind** | project field | `implementation`, `verification`, `audit`, `content` |

`Status` is the workflow state; `State` is the backlog's own vocabulary. Keep them consistent:

- planned, blocked or not started → `Status: Todo` with `State: Planned`, `Needs owner` or `Needs device`
- a session has claimed it, or the code is partly there → `Status: In Progress` with `State: Partial` or `Built (unverified)`
- merged or genuinely finished → `Status: Done` with `State: Done`

`Tier` is the owner's priority board order. Do not invent a tier: use `Unsorted` when the owner has not placed it, and `Decision` only for a numbered owner decision (D-rows).

## Add an item

```bash
PROJECT=PVT_…   # from the resolve step

gh api graphql -f query='
mutation($p:ID!, $t:String!, $b:String!) {
  addProjectV2DraftIssue(input:{projectId:$p, title:$t, body:$b}) {
    projectItem { id }
  }
}' -f p="$PROJECT" -f t='REP-074 · What it is, in one line' -f b='The row from the backlog, plus where it came from.'
```

A draft issue is the default: it lives on the board without adding noise to the repo's issue list. Use a real issue only when the owner asks for one.

Then set its fields. Field ids change per project, so resolve them first:

```bash
gh api graphql -f query='
query($p:ID!){ node(id:$p){ ... on ProjectV2 { fields(first:60){ nodes{
  ... on ProjectV2Field { id name }
  ... on ProjectV2SingleSelectField { id name options{ id name } }
} } } } }' -f p="$PROJECT"
```

and one mutation per field:

```bash
gh api graphql -f query='
mutation($p:ID!, $i:ID!, $f:ID!, $v:ProjectV2FieldValue!) {
  updateProjectV2ItemFieldValue(input:{projectId:$p, itemId:$i, fieldId:$f, value:$v}) {
    projectV2Item { id }
  }
}' -f p="$PROJECT" -f i="$ITEM" -f f="$FIELD" \
   -f v='{"singleSelectOptionId":"'"$OPTION"'}'
```

## Move an item

Same mutation, `fieldId` of **Status** and the option id of `Todo`, `In Progress` or `Done`. Merged and closed pull requests move themselves to `Done` — GitHub's built-in workflow does it, so do not fight it; just check it happened.

## Rules

- **One item per backlog row or per distinct piece of work.** Title a row `REP-nnn · <short title>` and a gap found by review `Doc gap · <imperative title>`.
- **Look before you add.** Page through `items(first:100, after:…)` and compare titles: the board already holds the whole open backlog, and a duplicate is worse than a missing item.
- **The body carries the evidence:** the row's text, what depends on it, and `docs/…:line` for a gap. Someone should be able to act on the item without reopening the doc.
- **Never mark `Done` for work that is only pushed.** Done means merged, or the owner said so. A PR in review is `In Progress`.
- **If `gh` is not authenticated** (no `GH_TOKEN`, no `gh auth`), say so in one line and continue the task; never block a code change on the board.
- **Do not print the token** or paste it into a command that gets logged.

## Views

The board is split by `Module` for reading: `All`, `PLAN`, `CARE`, `HEAL`, `MINT`, `BASE`, `LINK`, `GEAR`, `WORLD`, `Launcher`, plus `Implementation backlog` (`kind:implementation`). A new module or feature area that earns its own view needs a filter of the form `module:<option>`, set with `createProjectV2View` then `updateProjectV2View(viewId, filter)`.
