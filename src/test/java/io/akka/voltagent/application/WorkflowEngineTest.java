package io.akka.voltagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.voltagent.domain.RunStatus;
import io.akka.voltagent.domain.StepStatus;
import io.akka.voltagent.domain.WorkflowRunState;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 1, 3-8 — every step kind's own semantics, against a real running
 * entity, since {@link WorkflowEngine} drives {@link WorkflowRunEntity} through the
 * platform's own {@code ComponentClient} rather than an in-memory fake. */
public class WorkflowEngineTest extends TestKitSupport {

  private WorkflowEngine engine() {
    return new WorkflowEngine(componentClient);
  }

  private String id() {
    return "run-" + UUID.randomUUID();
  }

  private WorkflowRunState state(String id) {
    return componentClient.forEventSourcedEntity(id).method(WorkflowRunEntity::get).invoke();
  }

  @Test
  public void startsFreshExecutionIdPerRun() {
    var def = new WorkflowDefinition("wf-1", "id-check",
        List.of(new Step.Func("s0", ctx -> ctx.data())));

    var r1 = engine().run(def, null, "a");
    var r2 = engine().run(def, null, "a");

    assertThat(r1.executionId()).isNotEqualTo(r2.executionId());
  }

  @Test
  public void funcStepReplacesDataAndPropagatesError() {
    var ok = new WorkflowDefinition("wf-2", "upper",
        List.of(new Step.Func("s0", ctx -> ((String) ctx.data()).toUpperCase())));
    var okResult = engine().run(ok, id(), "hello");
    assertThat(okResult.status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(okResult.output()).isEqualTo("HELLO");

    var boom = new WorkflowDefinition("wf-3", "boom",
        List.of(new Step.Func("s0", ctx -> {
          throw new IllegalStateException("kaboom");
        })));
    var failResult = engine().run(boom, id(), "hello");
    assertThat(failResult.status()).isEqualTo(RunStatus.ERROR);
    assertThat(failResult.error()).isEqualTo("kaboom");
  }

  @Test
  public void whenStepSkipsOnFalseCondition() {
    var def = new WorkflowDefinition("wf-4", "cond",
        List.of(new Step.When("s0", ctx -> false, new Step.Func("s0-nested", ctx -> "changed"))));
    String runId = id();
    var result = engine().run(def, runId, "unchanged");

    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(result.output()).isEqualTo("unchanged");
    assertThat(state(runId).steps().get(0).status()).isEqualTo(StepStatus.SKIPPED);
  }

  @Test
  public void whenStepRunsNestedStepOnTrueCondition() {
    var def = new WorkflowDefinition("wf-5", "cond",
        List.of(new Step.When("s0", ctx -> true, new Step.Func("s0-nested", ctx -> "changed"))));
    String runId = id();
    var result = engine().run(def, runId, "unchanged");

    assertThat(result.output()).isEqualTo("changed");
    var recorded = state(runId).steps().get(0);
    assertThat(recorded.status()).isEqualTo(StepStatus.SUCCESS);
    assertThat(recorded.kind().name()).isEqualTo("WHEN");
  }

  @Test
  public void allStepPreservesOriginalOrder() {
    var all = new Step.All("s0", List.of(
        new Step.Func("a", ctx -> 1),
        new Step.Func("b", ctx -> 2),
        new Step.Func("c", ctx -> 3)));
    var def = new WorkflowDefinition("wf-6", "parallel", List.of(all));

    var result = engine().run(def, id(), null);

    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
    @SuppressWarnings("unchecked")
    var output = (List<Object>) result.output();
    assertThat(output).containsExactly(1, 2, 3);
  }

  @Test
  public void allStepFailsWithLowestIndexedError() {
    var all = new Step.All("s0", List.of(
        new Step.Func("a", ctx -> {
          throw new IllegalStateException("first");
        }),
        new Step.Func("b", ctx -> {
          throw new IllegalStateException("second");
        }),
        new Step.Func("c", ctx -> "ok")));
    var def = new WorkflowDefinition("wf-7", "parallel-fail", List.of(all));

    var result = engine().run(def, id(), null);

    assertThat(result.status()).isEqualTo(RunStatus.ERROR);
    assertThat(result.error()).isEqualTo("first");
  }

  @Test
  public void tapStepSwallowsErrorsAndPassesDataThrough() {
    var calls = new AtomicInteger();
    var def = new WorkflowDefinition("wf-8", "tap", List.of(
        new Step.Tap("s0", ctx -> {
          calls.incrementAndGet();
          throw new IllegalStateException("ignored");
        })));

    String runId = id();
    var result = engine().run(def, runId, "unchanged");

    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(result.output()).isEqualTo("unchanged");
    assertThat(calls.get()).isEqualTo(1);
    assertThat(state(runId).steps().get(0).status()).isEqualTo(StepStatus.SUCCESS);
  }

  @Test
  public void sleepClampsNegativeDurationToZero() {
    var def = new WorkflowDefinition("wf-9", "sleep", List.of(new Step.Sleep("s0", -5000)));

    long started = System.currentTimeMillis();
    var result = engine().run(def, id(), "data");
    long elapsedMillis = System.currentTimeMillis() - started;

    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
    assertThat(result.output()).isEqualTo("data");
    assertThat(elapsedMillis).isLessThan(2000);
  }

  @Test
  public void runStatusReflectsStepOutcome() {
    var completes = new WorkflowDefinition("wf-10", "ok",
        List.of(new Step.Func("s0", ctx -> "done")));
    assertThat(engine().run(completes, id(), "x").status()).isEqualTo(RunStatus.COMPLETED);

    var errors = new WorkflowDefinition("wf-11", "bad", List.of(
        new Step.Func("s0", ctx -> {
          throw new RuntimeException("nope");
        }),
        new Step.Func("s1", ctx -> "never reached")));
    String runId = id();
    var result = engine().run(errors, runId, "x");
    assertThat(result.status()).isEqualTo(RunStatus.ERROR);
    // The second step never started -- the run stops on the first unrecovered error.
    assertThat(state(runId).steps()).hasSize(1);
  }
}
