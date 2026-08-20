package io.akka.voltagent;

import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.akka.voltagent.application.Step;
import io.akka.voltagent.application.WorkflowDefinition;
import io.akka.voltagent.application.WorkflowEngine;
import io.akka.voltagent.domain.RunStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Writes {@code bench/port-answers.json}, one entry per scenario, in the same shape
 * {@code bench/scenarios.spec.ts} writes {@code bench/source-answers.json} against the
 * real VoltAgent source -- so {@code bench/compare.py} diffs answers nobody transcribed
 * by hand. Run with {@code mvn -B verify -Dit.test=BenchAnswersIntegrationTest} (or plain
 * {@code -Dtest=} under surefire) after the review pass in
 * {@code ../voltagent-port/docs/review-findings.md} was applied. */
public class BenchAnswersIntegrationTest extends TestKitSupport {

  private WorkflowEngine engine() {
    return new WorkflowEngine(componentClient);
  }

  @Test
  public void writesPortAnswers() throws IOException {
    Map<String, Object> answers = new LinkedHashMap<>();

    // allStepPreservesOriginalOrder
    {
      var all = new Step.All("s0", List.of(
          new Step.Func("a", ctx -> 1),
          new Step.Func("b", ctx -> 2),
          new Step.Func("c", ctx -> 3)));
      var result = engine().run(new WorkflowDefinition("all-order", "all-order", List.of(all)),
          null, Map.of());
      answers.put("allStepPreservesOriginalOrder", Map.of(
          "status", result.status() == RunStatus.COMPLETED ? "completed" : "error",
          "output", result.output()));
    }

    // allStepFailsWithLowestIndexedError
    {
      var all = new Step.All("s0", List.of(
          new Step.Func("a", ctx -> {
            throw new IllegalStateException("first");
          }),
          new Step.Func("b", ctx -> {
            throw new IllegalStateException("second");
          }),
          new Step.Func("c", ctx -> "ok")));
      var result = engine().run(new WorkflowDefinition("all-fail", "all-fail", List.of(all)),
          null, Map.of());
      answers.put("allStepFailsWithLowestIndexedError", Map.of(
          "status", result.status() == RunStatus.COMPLETED ? "completed" : "error",
          "error", result.error()));
    }

    // whenStepSkipsOnFalseCondition
    {
      var when = new Step.When("s0", ctx -> false,
          new Step.Func("s0-nested", ctx -> "changed"));
      var result = engine().run(new WorkflowDefinition("when-skip", "when-skip", List.of(when)),
          null, "unchanged");
      answers.put("whenStepSkipsOnFalseCondition", Map.of(
          "status", result.status() == RunStatus.COMPLETED ? "completed" : "error",
          "output", result.output()));
    }

    // whenStepRunsNestedStepOnTrueCondition
    {
      var when = new Step.When("s0", ctx -> true,
          new Step.Func("s0-nested", ctx -> "changed"));
      var result = engine().run(new WorkflowDefinition("when-run", "when-run", List.of(when)),
          null, "unchanged");
      answers.put("whenStepRunsNestedStepOnTrueCondition", Map.of(
          "status", result.status() == RunStatus.COMPLETED ? "completed" : "error",
          "output", result.output()));
    }

    // tapStepSwallowsErrorsAndPassesDataThrough
    {
      var tap = new Step.Tap("s0", ctx -> {
        throw new IllegalStateException("ignored");
      });
      var result = engine().run(new WorkflowDefinition("tap-swallow", "tap-swallow", List.of(tap)),
          null, "unchanged");
      answers.put("tapStepSwallowsErrorsAndPassesDataThrough", Map.of(
          "status", result.status() == RunStatus.COMPLETED ? "completed" : "error",
          "output", result.output()));
    }

    // sleepClampsNegativeDurationToZero
    {
      long started = System.currentTimeMillis();
      var sleep = new Step.Sleep("s0", -5000);
      var result = engine().run(new WorkflowDefinition("sleep-clamp", "sleep-clamp", List.of(sleep)),
          null, "data");
      long elapsedMs = System.currentTimeMillis() - started;
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("status", result.status() == RunStatus.COMPLETED ? "completed" : "error");
      entry.put("output", result.output());
      entry.put("elapsedUnderTwoSeconds", elapsedMs < 2000);
      answers.put("sleepClampsNegativeDurationToZero", entry);
    }

    Path outPath = Path.of("..", "voltagent-port", "bench", "port-answers.json");
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), answers);
  }

  @Test
  public void writesPortTiming() throws IOException {
    int warmup = 20;
    int iterations = 200;
    var def = new WorkflowDefinition("timing", "timing",
        List.of(new Step.Func("s0", ctx -> ctx.data())));

    for (int i = 0; i < warmup; i++) {
      engine().run(def, null, "x");
    }

    long started = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      engine().run(def, null, "x");
    }
    long elapsedNs = System.nanoTime() - started;

    Map<String, Object> timing = new LinkedHashMap<>();
    timing.put("iterations", iterations);
    timing.put("nsPerOp", elapsedNs / iterations);
    timing.put("what", "one WorkflowEngine.run() with a single func step, through the real "
        + "ComponentClient to a real WorkflowRunEntity -- two persisted events (RunStarted, "
        + "StepStarted+StepSucceeded, RunCompleted) written to the actual event journal.");

    Path outPath = Path.of("..", "voltagent-port", "bench", "port-timing.json");
    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), timing);
  }
}
