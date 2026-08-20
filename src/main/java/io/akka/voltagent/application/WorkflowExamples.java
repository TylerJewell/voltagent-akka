package io.akka.voltagent.application;

import java.util.List;

/** Example workflow definitions, registered once at startup the way an embedding
 * application registers its own workflows against VoltAgent's {@code createWorkflow} —
 * SPEC-001 §2: a definition is created once and held in the registry, never itself
 * persisted or driven through an API. {@link api.WorkflowEndpoint} runs these by id. */
public final class WorkflowExamples {

  private WorkflowExamples() {}

  public static final String DEMO_WORKFLOW_ID = "demo";

  /** Exercises every ported step kind in one pipeline: {@code func} upper-cases the
   * input, {@code when} appends "!" only for short inputs, {@code all} runs two
   * independent length checks in parallel, {@code tap} logs without changing the data,
   * {@code sleep} pauses briefly before the run completes. */
  public static WorkflowDefinition demo() {
    Step upper = new Step.Func("upper", ctx -> ((String) ctx.data()).toUpperCase());

    Step maybeExclaim = new Step.When("maybe-exclaim",
        ctx -> ((String) ctx.data()).length() < 20,
        new Step.Func("exclaim", ctx -> ctx.data() + "!"));

    Step lengthChecks = new Step.All("length-checks", List.of(
        new Step.Func("is-short", ctx -> ((String) ctx.data()).length() < 20),
        new Step.Func("word-count", ctx -> ((String) ctx.data()).split("\\s+").length)));

    Step log = new Step.Tap("log", ctx -> {});

    Step pause = new Step.Sleep("pause", 10);

    return new WorkflowDefinition(DEMO_WORKFLOW_ID, "demo", "Exercises every ported step kind",
        List.of(upper, maybeExclaim, lengthChecks, log, pause));
  }

  public static void registerAll(WorkflowRegistry registry) {
    registry.register(demo());
  }
}
