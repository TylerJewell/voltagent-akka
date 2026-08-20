package io.akka.voltagent.domain;

/** A single step's own status within a run — SPEC-001 §2. Only a {@code when} step can
 * reach {@code SKIPPED}; a {@code tap} step can never reach {@code ERROR} — SPEC-001 §3. */
public enum StepStatus {
  RUNNING,
  SUCCESS,
  SKIPPED,
  ERROR
}
