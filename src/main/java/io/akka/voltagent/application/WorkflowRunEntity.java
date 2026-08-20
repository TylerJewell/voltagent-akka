package io.akka.voltagent.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.voltagent.domain.StepKind;
import io.akka.voltagent.domain.WorkflowRunEvent;
import io.akka.voltagent.domain.WorkflowRunState;
import java.time.Instant;

/** One workflow run's own durable history — SPEC-001 §2, §3. The entity id is the run's
 * own id ({@code executionId} in the source, question-log row 11), distinct from the
 * {@code workflowId} of the definition that produced it. */
@Component(id = "workflow-run")
public class WorkflowRunEntity extends EventSourcedEntity<WorkflowRunState, WorkflowRunEvent> {

  private final String entityId;

  public WorkflowRunEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public WorkflowRunState emptyState() {
    return WorkflowRunState.empty(entityId);
  }

  public record Start(String workflowId, String workflowName, Object input) {}

  public Effect<Done> start(Start command) {
    return effects()
        .persist(new WorkflowRunEvent.RunStarted(command.workflowId(), command.workflowName(),
            Instant.now(), command.input()))
        .thenReply(state -> Done.getInstance());
  }

  public record StepStarted(int stepIndex, String stepId, String stepName, StepKind kind,
      Object input) {}

  public Effect<Done> stepStarted(StepStarted command) {
    return effects()
        .persist(new WorkflowRunEvent.StepStarted(command.stepIndex(), command.stepId(),
            command.stepName(), command.kind(), Instant.now(), command.input()))
        .thenReply(state -> Done.getInstance());
  }

  public record StepOutcome(int stepIndex, Object output) {}

  public Effect<Done> stepSucceeded(StepOutcome command) {
    return effects()
        .persist(new WorkflowRunEvent.StepSucceeded(command.stepIndex(), Instant.now(),
            command.output()))
        .thenReply(state -> Done.getInstance());
  }

  public Effect<Done> stepSkipped(StepOutcome command) {
    return effects()
        .persist(new WorkflowRunEvent.StepSkipped(command.stepIndex(), Instant.now(),
            command.output()))
        .thenReply(state -> Done.getInstance());
  }

  public record StepError(int stepIndex, String error) {}

  public Effect<Done> stepFailed(StepError command) {
    return effects()
        .persist(new WorkflowRunEvent.StepFailed(command.stepIndex(), Instant.now(),
            command.error()))
        .thenReply(state -> Done.getInstance());
  }

  public Effect<Done> completeRun(Object output) {
    return effects()
        .persist(new WorkflowRunEvent.RunCompleted(Instant.now(), output))
        .thenReply(state -> Done.getInstance());
  }

  public Effect<Done> failRun(String error) {
    return effects()
        .persist(new WorkflowRunEvent.RunFailed(Instant.now(), error))
        .thenReply(state -> Done.getInstance());
  }

  public ReadOnlyEffect<WorkflowRunState> get() {
    if (currentState().isEmpty()) {
      return effects().error("run not found");
    }
    return effects().reply(currentState());
  }

  @Override
  public WorkflowRunState applyEvent(WorkflowRunEvent event) {
    return currentState().apply(event);
  }
}
