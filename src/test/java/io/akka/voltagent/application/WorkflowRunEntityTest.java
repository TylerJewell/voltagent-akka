package io.akka.voltagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.voltagent.domain.RunStatus;
import io.akka.voltagent.domain.StepKind;
import io.akka.voltagent.domain.StepStatus;
import io.akka.voltagent.domain.WorkflowRunEvent;
import io.akka.voltagent.domain.WorkflowRunState;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 2, 9 against the entity directly — the ordering a run loop cannot
 * demonstrate by itself, since it is the entity, not the caller, that decides what a
 * mid-run read sees. */
public class WorkflowRunEntityTest {

  private EventSourcedTestKit<WorkflowRunState, WorkflowRunEvent, WorkflowRunEntity> run() {
    return EventSourcedTestKit.of("run-1", WorkflowRunEntity::new);
  }

  @Test
  public void recordsStepRunningBeforeCompletion() {
    var kit = run();
    kit.method(WorkflowRunEntity::start)
        .invoke(new WorkflowRunEntity.Start("wf-1", "greet", "hello"));

    kit.method(WorkflowRunEntity::stepStarted)
        .invoke(new WorkflowRunEntity.StepStarted(0, "s0", "upper", StepKind.FUNC, "hello"));

    // Mid-run: the step is recorded as running before it has settled.
    WorkflowRunState midRun = kit.getState();
    assertThat(midRun.status()).isEqualTo(RunStatus.RUNNING);
    assertThat(midRun.steps()).hasSize(1);
    assertThat(midRun.steps().get(0).status()).isEqualTo(StepStatus.RUNNING);
    assertThat(midRun.steps().get(0).endTime()).isNull();

    kit.method(WorkflowRunEntity::stepSucceeded)
        .invoke(new WorkflowRunEntity.StepOutcome(0, "HELLO"));
    kit.method(WorkflowRunEntity::completeRun).invoke("HELLO");

    WorkflowRunState done = kit.getState();
    assertThat(done.status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(done.output()).isEqualTo("HELLO");
    assertThat(done.steps().get(0).status()).isEqualTo(StepStatus.SUCCESS);
    assertThat(done.steps().get(0).endTime()).isNotNull();
  }

  @Test
  public void aFailedStepFailsTheRunAndStopsFurtherSteps() {
    var kit = run();
    kit.method(WorkflowRunEntity::start)
        .invoke(new WorkflowRunEntity.Start("wf-1", "greet", "hello"));
    kit.method(WorkflowRunEntity::stepStarted)
        .invoke(new WorkflowRunEntity.StepStarted(0, "s0", "boom", StepKind.FUNC, "hello"));
    kit.method(WorkflowRunEntity::stepFailed)
        .invoke(new WorkflowRunEntity.StepError(0, "kaboom"));
    kit.method(WorkflowRunEntity::failRun).invoke("kaboom");

    WorkflowRunState state = kit.getState();
    assertThat(state.status()).isEqualTo(RunStatus.ERROR);
    assertThat(state.error()).isEqualTo("kaboom");
    assertThat(state.steps().get(0).status()).isEqualTo(StepStatus.ERROR);
    assertThat(state.steps().get(0).error()).isEqualTo("kaboom");
  }
}
