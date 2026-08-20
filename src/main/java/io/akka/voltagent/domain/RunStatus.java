package io.akka.voltagent.domain;

/** A run's own status — SPEC-001 §2. Not carried: the source's `suspended`/`cancelled`,
 * both tied to the out-of-scope suspend/resume capability. */
public enum RunStatus {
  RUNNING,
  COMPLETED,
  ERROR
}
