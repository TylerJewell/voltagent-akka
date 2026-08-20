package io.akka.voltagent.domain;

import java.time.Instant;

/** One step's own record within a run — SPEC-001 §2. */
public record StepRun(
    int stepIndex,
    String stepId,
    String stepName,
    StepKind kind,
    StepStatus status,
    Instant startTime,
    Instant endTime,
    Object input,
    Object output,
    String error) {

  public StepRun started(Instant startTime, Object input) {
    return new StepRun(stepIndex, stepId, stepName, kind, StepStatus.RUNNING, startTime, null,
        input, null, null);
  }

  public StepRun settled(StepStatus status, Instant endTime, Object output, String error) {
    return new StepRun(stepIndex, stepId, stepName, kind, status, startTime, endTime, input,
        output, error);
  }
}
