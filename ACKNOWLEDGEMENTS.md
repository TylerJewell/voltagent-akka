# Acknowledgements

This project is a port of **[VoltAgent/voltagent](https://github.com/VoltAgent/voltagent)**.

## Licence of the original

**MIT**, © 2025 VoltAgent Inc. Read from the `LICENCE` file at the root of the repository
at commit `35efe17`, not from a badge.

## What was copied

**No source was copied.** No file, function, class or fragment of `VoltAgent/voltagent`
appears in this project. Everything here is written against a behavioural specification —
`voltagent-port/specs/SPEC-001-voltagent.md` in the harness repository — and the Java in
`src/main` shares no text with the TypeScript it was derived from.

Two things did cross over, and neither is source:

- **The behaviour itself.** Which step kinds exist and what each one does to the run's
  data on success, failure and (for `when`) a false condition; that `all` waits for every
  sub-step regardless of failure and reports the lowest-indexed error; that a step's
  running record is written before it executes and settled after; that a run's own id is
  distinct from its definition's id — all of this is derived from `VoltAgent/voltagent`
  and reproduces it deliberately. That is what a port is, and it is not something to be
  coy about.
- **Scenario inputs.** `voltagent-port/bench/scenarios.spec.ts` in the harness repository
  drives VoltAgent's own `createWorkflowChain`/`andAll`/`andWhen`/`andTap`/`andSleep`
  through six small scenarios, written for this port's own correctness comparison; none
  is copied from the original's own test suite (`packages/core/src/workflow/**/*.spec.ts`),
  though the *expected outcomes* were read from those files during step c — see
  `voltagent-port/docs/question-log.md` rows 4, 6, 7, 9.

The probes and benchmark runner in the harness repository import and run
`VoltAgent/voltagent`'s own source unmodified, from a clone kept beside the harness. They
live there, not here, and this project does not depend on it at build time or at run time.

## What that means for this project's licence

MIT is a permissive licence and imposes no share-alike obligation, so nothing about the
original constrains what this project may be licensed as. Its attribution clause applies
to redistributed copies of its own source, and none is included here; the attribution
above is given because it is owed to the work this was derived from, not because a copied
file forces it.

## Also used

- **[Akka](https://akka.io)** — the SDK and runtime this port is built on
  (`akka-javasdk` 3.6.3, Business Source License 1.1).
