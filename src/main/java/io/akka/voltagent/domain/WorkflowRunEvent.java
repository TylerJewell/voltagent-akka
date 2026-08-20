package io.akka.voltagent.domain;

import akka.javasdk.annotations.TypeName;
import java.time.Instant;

/** Everything that can happen during one workflow run — SPEC-001 §3 rule 2. */
public sealed interface WorkflowRunEvent {

  @TypeName("run-started")
  record RunStarted(String workflowId, String workflowName, Instant startTime, Object input)
      implements WorkflowRunEvent {}

  @TypeName("step-started")
  record StepStarted(int stepIndex, String stepId, String stepName, StepKind kind,
      Instant startTime, Object input) implements WorkflowRunEvent {}

  @TypeName("step-succeeded")
  record StepSucceeded(int stepIndex, Instant endTime, Object output)
      implements WorkflowRunEvent {}

  @TypeName("step-skipped")
  record StepSkipped(int stepIndex, Instant endTime, Object output)
      implements WorkflowRunEvent {}

  @TypeName("step-failed")
  record StepFailed(int stepIndex, Instant endTime, String error)
      implements WorkflowRunEvent {}

  @TypeName("run-completed")
  record RunCompleted(Instant endTime, Object output) implements WorkflowRunEvent {}

  @TypeName("run-failed")
  record RunFailed(Instant endTime, String error) implements WorkflowRunEvent {}
}
