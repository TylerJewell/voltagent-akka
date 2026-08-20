package io.akka.voltagent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A single run's own record — SPEC-001 §2. {@code id} is this run's own id, distinct
 * from {@code workflowId}, the stable id of the definition that produced it (question-log
 * row 11). */
public record WorkflowRunState(
    String id,
    String workflowId,
    String workflowName,
    RunStatus status,
    Object input,
    Object output,
    String error,
    Instant startTime,
    Instant endTime,
    List<StepRun> steps) {

  public static WorkflowRunState empty(String id) {
    return new WorkflowRunState(id, null, null, null, null, null, null, null, null, List.of());
  }

  public boolean isEmpty() {
    return workflowId == null;
  }

  public WorkflowRunState apply(WorkflowRunEvent event) {
    return switch (event) {
      case WorkflowRunEvent.RunStarted e -> new WorkflowRunState(id, e.workflowId(),
          e.workflowName(), RunStatus.RUNNING, e.input(), null, null, e.startTime(), null,
          List.of());

      case WorkflowRunEvent.StepStarted e -> {
        var updated = new ArrayList<>(steps);
        updated.add(new StepRun(e.stepIndex(), e.stepId(), e.stepName(), e.kind(),
            StepStatus.RUNNING, e.startTime(), null, e.input(), null, null));
        yield withSteps(updated);
      }

      case WorkflowRunEvent.StepSucceeded e ->
          withSteps(settle(e.stepIndex(), StepStatus.SUCCESS, e.endTime(), e.output(), null));

      case WorkflowRunEvent.StepSkipped e ->
          withSteps(settle(e.stepIndex(), StepStatus.SKIPPED, e.endTime(), e.output(), null));

      case WorkflowRunEvent.StepFailed e ->
          withSteps(settle(e.stepIndex(), StepStatus.ERROR, e.endTime(), null, e.error()));

      case WorkflowRunEvent.RunCompleted e -> new WorkflowRunState(id, workflowId, workflowName,
          RunStatus.COMPLETED, input, e.output(), null, startTime, e.endTime(), steps);

      case WorkflowRunEvent.RunFailed e -> new WorkflowRunState(id, workflowId, workflowName,
          RunStatus.ERROR, input, null, e.error(), startTime, e.endTime(), steps);
    };
  }

  private List<StepRun> settle(int stepIndex, StepStatus status, Instant endTime, Object output,
      String error) {
    var updated = new ArrayList<>(steps);
    var running = updated.get(stepIndex);
    updated.set(stepIndex, running.settled(status, endTime, output, error));
    return updated;
  }

  private WorkflowRunState withSteps(List<StepRun> updated) {
    return new WorkflowRunState(id, workflowId, workflowName, status, input, output, error,
        startTime, endTime, List.copyOf(updated));
  }
}
