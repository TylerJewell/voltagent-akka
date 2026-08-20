package io.akka.voltagent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.voltagent.api.WorkflowEndpoint;
import io.akka.voltagent.application.WorkflowEngine;
import io.akka.voltagent.application.WorkflowExamples;
import io.akka.voltagent.application.WorkflowRunsView;
import io.akka.voltagent.domain.RunStatus;
import io.akka.voltagent.domain.WorkflowRunState;
import io.akka.voltagent.domain.WorkflowStats;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules 9-11, driven through {@link WorkflowEndpoint} the way a real caller
 * would: a run and its steps are readable by id, runs are listable and filterable across
 * a workflow, and per-workflow statistics are computed from that same persisted history
 * rather than stubbed (question-log row 12, spec OD-2). This capability has no interface
 * beyond its own HTTP surface -- see {@code gui/manifest.json} in the findings directory. */
public class WorkflowRunIntegrationTest extends TestKitSupport {

  private WorkflowEndpoint.RunRequest request(Object input) {
    return new WorkflowEndpoint.RunRequest(input);
  }

  private WorkflowEngine.RunResult run(Object input) {
    return httpClient
        .POST("/workflows/" + WorkflowExamples.DEMO_WORKFLOW_ID + "/runs")
        .withRequestBody(request(input))
        .responseBodyAs(WorkflowEngine.RunResult.class)
        .invoke()
        .body();
  }

  @Test
  public void readsRunningAndCompletedHistory() {
    var result = run("hi there");
    assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);

    var state = httpClient
        .GET("/workflows/runs/" + result.executionId())
        .responseBodyAs(WorkflowRunState.class)
        .invoke()
        .body();

    assertThat(state.id()).isEqualTo(result.executionId());
    assertThat(state.workflowId()).isEqualTo(WorkflowExamples.DEMO_WORKFLOW_ID);
    assertThat(state.status()).isEqualTo(RunStatus.COMPLETED);
    // func -> when -> all -> tap -> sleep: five steps recorded, in order.
    assertThat(state.steps()).hasSize(5);
  }

  @Test
  public void listsRunsByWorkflowAndStatus() {
    run("ok");
    run("also ok");
    run(""); // split("\\s+") on empty string still succeeds; every demo run completes.

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var all = httpClient
          .GET("/workflows/" + WorkflowExamples.DEMO_WORKFLOW_ID + "/runs")
          .responseBodyAs(WorkflowRunsView.Runs.class)
          .invoke()
          .body();
      assertThat(all.runs().size()).isGreaterThanOrEqualTo(3);

      var completedOnly = httpClient
          .GET("/workflows/" + WorkflowExamples.DEMO_WORKFLOW_ID + "/runs/status/COMPLETED")
          .responseBodyAs(WorkflowRunsView.Runs.class)
          .invoke()
          .body();
      assertThat(completedOnly.runs().size()).isGreaterThanOrEqualTo(3);

      var errorOnly = httpClient
          .GET("/workflows/" + WorkflowExamples.DEMO_WORKFLOW_ID + "/runs/status/ERROR")
          .responseBodyAs(WorkflowRunsView.Runs.class)
          .invoke()
          .body();
      assertThat(errorOnly.runs()).allSatisfy(r -> assertThat(r.status()).isEqualTo("ERROR"));
    });
  }

  @Test
  public void computesStatsFromHistory() {
    run("one more run for the stats window");

    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      var stats = httpClient
          .GET("/workflows/" + WorkflowExamples.DEMO_WORKFLOW_ID + "/stats")
          .responseBodyAs(WorkflowStats.class)
          .invoke()
          .body();

      assertThat(stats.totalExecutions()).isGreaterThan(0);
      assertThat(stats.lastExecutionTime()).isNotNull();
    });
  }
}
