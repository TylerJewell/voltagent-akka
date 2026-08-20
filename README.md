# voltagent-akka

Runs a workflow as an ordered list of typed steps, and keeps a durable, queryable record
of exactly what each run did — which steps ran, in what order, with what status, and what
the run as a whole produced.

A port of [VoltAgent/voltagent](https://github.com/VoltAgent/voltagent) onto **Akka**,
built with **Akka Specify**.

---

## Where it came from

VoltAgent is a framework for building AI agents in TypeScript; its workflow package lets
an application chain together steps — run a function, run one only if a condition holds,
run several in parallel, run a side effect, pause — and records what a run of that chain
did. This port takes only that: the workflow definition, the run loop, and the run's own
history. Left alone: suspend-and-resume, time-travel replay, steps that call a language
model, streaming to external clients, guardrails, and VoltAgent's own cloud dashboard —
each is a separate concern layered on the same step loop, not this capability itself.

The specification this was built from lives in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `voltagent-port/`.

---

## VoltAgent/voltagent → this port

📉 5,592 TypeScript lines → **535 Java lines**<br>
📁 11 files → **17 files**<br>
⚡ 1,675,927 → **10,474,902** nanoseconds, one workflow run, single step<br>
🎯 6 scenarios compared → **6 of 6 agree**<br>
🧪 0 rules broken on purpose to check a test notices → **4**

The timing is each system doing a different amount of work, not only the same decision
twice: the source measurement keeps nothing beyond one process's memory, and this port
writes four events to a real, replayable journal before answering. How each number was
measured, including why the line count is a conservative one, is written up next to the
specification in `akka-specify-harness` under `voltagent-port/bench/REPORT.md`.

---

## What it took to build

⏱️ **2.5 hours** from the first command to the published repository, **1.2** of them active<br>
💬 **515** exchanges with the model<br>
✍️ **272,148** tokens written by the model, **83,470,807** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **16** tests

```bash
python toolkit/tokens.py --port voltagent
```

The record of every question, and where the time went, lives with the specification.

---

## What it does

- **A workflow definition is a stable id, a name, a purpose, and an ordered list of
  steps** — created once and held in memory, never itself persisted, the same way the
  source's own `Workflow` object is distinct from any one run of it.
- **A step is one of five kinds:** run a function and replace the run's data with its
  result; run a nested step only if a condition holds, otherwise leave the data alone;
  run several steps together and wait for all of them; run a side effect that can never
  fail the run or change its data; pause for a duration.
- **Every step that runs gets its own record: order, kind, status, input, output, error,
  start and end time — written as `running` before the step executes, and settled after.**
  A run's history is readable, by its own id, at any point during or after the run,
  including while it is still in progress.
- **Running several steps together waits for every one of them, and reports results in
  their original order, not the order they finished in.** If one or more fail, the whole
  group fails with the lowest-indexed sub-step's error, and no partial output is returned.
- **A side effect step is never the reason a run fails, and never changes what the run is
  carrying** — an error inside it is caught and only logged.
- **Runs are listable and filterable by which workflow produced them and by their status**,
  and per-workflow statistics — how many runs, how many succeeded, how many failed, the
  average duration, the last run's time — are computed from that same real history, not
  returned as a stub.

---

## Design decisions

**Definitions live in code, not behind an API.** The source's own `createWorkflow` is
called once by the embedding application, never by an external caller; this port's HTTP
endpoint runs a definition by id and reads back its history, but does not accept a new
definition over the wire. That matches how the capability is actually used on both sides:
a workflow's shape is a deployment-time decision, a run of it is a request-time one.

**A run's history is a typed record, not a flat event list.** The source's actually-used
history is one loosely-typed array mixing workflow- and step-level entries by a `type`
field; the source's own normalized schema for this exists in its types but is never
constructed anywhere the port could find. This port gives a run and its steps their own
typed fields, finishing a design the source declared but never wired up, rather than
copying the flat list that replaced it.

**Per-workflow statistics are computed for real.** The source's equivalent method always
returns zero for every field — a stub, by its own comment, waiting on an async version
that does not exist. This port reads the real numbers back from the same durable history
every other query answers from.

**A `when` step's skip is an explicit signal, not an accident of object identity.** The
source decides a conditional step was skipped by comparing its output to the run's input
by reference — a check with no equivalent once data passes through immutable records and
boxed values the way this port's pipeline does. The port has the step report whether its
condition held directly, and the run loop reads that report instead of guessing from
identity.

**A finished run's own ending is part of its own history.** The source writes a run's
event list to storage, and only afterwards appends that write's own completion event to
the buffer that feeds the *next* write — so a completed run's saved timeline never
actually contains the event that says it finished. This port records a run's steps and
its own completion as of the same write, so a finished run's history is not missing its
own ending.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/voltagent-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3.** The service answers on **port 9021**. There is no page to open — it is a service
other programs talk to.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

### Try it

Run the built-in demo workflow, which exercises every ported step kind in one pipeline,
and read back its history:

```bash
curl -X POST localhost:9021/workflows/demo/runs \
  -H 'Content-Type: application/json' -d '{"input": "hello there"}'

curl localhost:9021/workflows/runs/<executionId from the response above>

curl localhost:9021/workflows/demo/runs
curl localhost:9021/workflows/demo/runs/status/COMPLETED
curl localhost:9021/workflows/demo/stats
```

---

## What it answers

| Request | What it does |
|---|---|
| `POST /workflows/{workflowId}/runs` | Run a registered workflow definition against the given input |
| `GET /workflows/runs/{executionId}` | One run's full history: its own status and output, and every step it recorded |
| `GET /workflows/{workflowId}/runs` | Every run of a workflow, most recent first |
| `GET /workflows/{workflowId}/runs/status/{status}` | The same, filtered to `RUNNING`, `COMPLETED`, or `ERROR` |
| `GET /workflows/{workflowId}/stats` | How many runs, how many succeeded or failed, the average duration, the last run's time |

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | | The port is set in `src/main/resources/application.conf`; nothing else is configurable |

---

## Where it differs from VoltAgent/voltagent

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **A run's history is typed, not a flat event array.** See "Design decisions" above.
  Anything that read the source's `events[]` directly would need to be rewritten against
  this port's `WorkflowRunState`/`StepRun` shape.
- **Per-workflow statistics are real numbers, not a stub.** A caller of the source that
  learned to expect zeroes from `getWorkflowStats()` will see real counts here instead.
- **A `when` step's skip is reported explicitly rather than inferred from object
  identity.** Observably the same outcome (§3 rule 4 of the specification); the mechanism
  a caller would be relying on, if any caller relied on the source's specific mechanism,
  is different.
- **A finished run's saved history includes its own completion event.** The source's does
  not, for the reason given above. Not checked: whether any caller of the source depends
  on that event being absent.
- **`sleep-until` — pausing until a target date rather than for a duration — is not
  ported.** Only duration-based `sleep` is. A caller using `sleep-until` today has no
  equivalent here.
- **A workflow run's own input may be `null`.** The source's own state accessor cannot
  tell a `null` input from a run whose state was never set, and throws on every later read
  of a run started that way (`packages/core/src/workflow/internal/state.ts:121`) — found
  while writing this port's own benchmark scenarios, not by reading. This port's
  `WorkflowEngine` treats `null` like any other input value.
- **A workflow definition cannot be registered over this port's own HTTP endpoint.**
  Definitions are registered in code at startup, the same way the source's own
  `createWorkflow` is called by the embedding application rather than by an external
  caller — but the source has no HTTP layer of its own to compare against for this
  specific capability, so this is a decision this port made on its own, not a measured
  difference from one the source has.

---

## Licence

MIT, the same as the original. See `ACKNOWLEDGEMENTS.md` for what was and was not carried
over from `VoltAgent/voltagent`, and why nothing about its licence constrains this one.
