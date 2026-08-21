# Handoff — Make the serving app actually servable

> **Created**: 2026-07-31 · **For**: a fresh session in `~/projects/agentworks-pr-review` (`v3-serving`)
> **Boot instruction**: *"Read `plans/HANDOFF-serving-app-startability-2026-07-31.md` and follow it."*
> **Origin**: filed by `agento-university` Track B Step 1.0, escalated by supervisor verification —
> the producer-side act lives at `agent-workflow/plans/v3/ROADMAP.md` § Carried acts, *"The serving
> app has never been verified to start."*

## The finding, measured

This repo is described across the program as **"the live serving app
(`GET /workflow/v3alpha/view` + `/catalog`)"**. It has never been verified to start.

| measured | |
|---|---|
| `@SpringBootTest` / `contextLoads()` / any context test | **none, anywhere** |
| `WorkflowV3AlphaControllerTest` | `MockMvcBuilders.standaloneSetup(new WorkflowV3AlphaController())` — the controller is built **by hand with `new`**; Spring wiring is never exercised |
| `JournalConfig.agentClient(AgentClient.Builder)` | requires a builder bean **with no provider** |
| profiles | `PrReviewRunner` is `@Profile("!agent-experiment")`, `AgentExperimentReviewRunner` is `@Profile("agent-experiment")` — **both are `CommandLineRunner`s, so every profile does work at boot** |

So: unstartable, for an unknown duration, with **every gate green**.

**Why it survived**: a serving app that cannot boot without firing a live LLM call **cannot run in
CI** — so nothing ever ran it, so nothing ever caught that it cannot run. The missing bean is the
symptom; *un-CI-ability* is the cause. Fixing only the bean would leave the hole open.

**What it costs in claims** — restate, do not defend: producer Step 2.4's *"SERVE a `WorkflowView`
over a provenance-bearing spec"* and the closure of canvas-spike **CS-6's live half** rest on the
**committed resource file**, not on a served response. Supervisor verification (19/19 nodes carrying
`source`) read the JSON on disk and shared the same gap. **The artifact claim is sound; the serving
claim is not, until a running app demonstrates it.**

## Work items

- [ ] **VERIFY the finding first, before changing anything.** Confirm each row of the table above
      against the code, and confirm the app actually fails to start (`./mvnw spring-boot:run`, or a
      `contextLoads` that you watch go red *before* you fix it). If any row is wrong, that is the
      finding — say so and stop.
- [ ] **ADD `contextLoads()`** — the standard `@SpringBootTest` smoke test. This is the act's core:
      one test that asserts the application context can be built. It would have caught this on day
      one.
- [ ] **PROVIDE or profile-guard the missing `AgentClient.Builder`** so the default profile wires.
- [ ] **MAKE ONE PROFILE BOOT WITHOUT EXTERNAL CALLS.** Both existing profiles run a
      `CommandLineRunner` at startup. Add a **serving-only profile** (or make the runners
      conditional) such that the app can start, serve `/workflow/v3alpha/view` and `/catalog`, and
      do nothing else — no LLM call, no PR fetch, no spend. *This is the item that keeps the defect
      from recurring*: without it the app stays un-CI-able and the next regression is again
      invisible.
- [ ] **TEST THE ENDPOINTS AGAINST A REAL CONTEXT** — a `@SpringBootTest` (webEnvironment RANDOM_PORT
      or a non-standalone MockMvc) hitting both endpoints. Keep the existing standalone test if you
      like; it is not a substitute. *(Context: `agent-workflow/CLAUDE.md` records "MockMvc beans may
      not materialize in a slice — use standalone `MockMvcBuilders`" as a Boot 4 workaround; that
      tactical note quietly became this repo's **only** controller coverage. If the slice problem
      recurs, solve it — do not fall back to standalone as the whole story.)*
- [ ] **FALSIFY BEFORE TRUSTING**: remove the bean provider again and watch `contextLoads` go red;
      restore it. Record the observation. A smoke test nobody has watched fail is not evidence.
- [ ] **RE-ASSERT CS-6's live half against an actual response** — fetch `/view` from the running app
      and confirm 19/19 nodes carry `source`. Then, and only then, the "live serving app" language is
      earned.
- [ ] **REPORT BACK to the producer** — write a short note into
      `~/projects/agent-workflow/plans/v3/inbox/` stating what was verified and against what
      evidence, so the carried act can close on a response rather than a file. Do not edit anything
      else in that repo.

## Constraints

Nothing in `agent-workflow` changes except the inbox note · no v3alpha wire shape, schema or
committed artifact moves — this is app wiring and tests · the byte-pinned emitted spec
(`src/main/resources/v3alpha/workflow-pr-review.json`) must not move; if it does, stop, because
something changed that this act does not touch · keep the existing profiles working.

## Anti-patterns

- **Do not fix only the bean.** The act's point is that the app must be startable *in CI, without
  spend*; a wired-but-still-un-runnable app reproduces the defect with a green test.
- **Do not weaken the runners' behaviour on existing profiles** to achieve it — add a profile, or
  make the runners conditional; do not silently stop doing the work someone relies on.
- **Do not re-assert the live claim from a file.** That is the error being corrected.

## Done =

`contextLoads` exists and was watched failing · one profile boots with no external calls · both
endpoints tested against a real context · CS-6's live half re-asserted against a served response ·
`./mvnw verify` green · the emitted spec byte-unchanged · a note filed into the producer's inbox ·
a short record at `plans/learnings/` per this repo's convention.
