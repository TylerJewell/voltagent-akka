package io.akka.voltagent.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.voltagent.application.WorkflowEngine;
import io.akka.voltagent.application.WorkflowExamples;
import io.akka.voltagent.application.WorkflowRegistry;
import io.akka.voltagent.application.WorkflowRunEntity;
import io.akka.voltagent.application.WorkflowRunsView;
import io.akka.voltagent.application.WorkflowStatsCalculator;
import io.akka.voltagent.domain.WorkflowRunState;
import io.akka.voltagent.domain.WorkflowStats;

/** Runs a registered workflow definition and reads back the run history it records about
 * itself — SPEC-001 §1. Definitions are registered in code at startup ({@link
 * WorkflowExamples}), the same way VoltAgent's own {@code createWorkflow} is called once
 * by the embedding application, never through this API — only running a definition and
 * reading its history is. This is the port's only external surface; see
 * {@code gui/manifest.json} in the findings directory for why it has no rendered one. */
@HttpEndpoint("/workflows")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class WorkflowEndpoint {

  private final ComponentClient componentClient;
  private final WorkflowRegistry registry;

  public WorkflowEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
    this.registry = new WorkflowRegistry();
    WorkflowExamples.registerAll(registry);
  }

  public record RunRequest(Object input) {}

  @Post("/{workflowId}/runs")
  public WorkflowEngine.RunResult run(String workflowId, RunRequest request) {
    var definition = registry.get(workflowId);
    if (definition == null) {
      throw new IllegalArgumentException("no such workflow: " + workflowId);
    }
    return new WorkflowEngine(componentClient).run(definition, null, request.input());
  }

  @Get("/runs/{executionId}")
  public WorkflowRunState getRun(String executionId) {
    return componentClient.forEventSourcedEntity(executionId)
        .method(WorkflowRunEntity::get)
        .invoke();
  }

  @Get("/{workflowId}/runs")
  public WorkflowRunsView.Runs listRuns(String workflowId) {
    return componentClient.forView().method(WorkflowRunsView::byWorkflow).invoke(workflowId);
  }

  @Get("/{workflowId}/runs/status/{status}")
  public WorkflowRunsView.Runs listRunsByStatus(String workflowId, String status) {
    return componentClient.forView()
        .method(WorkflowRunsView::byWorkflowAndStatus)
        .invoke(new WorkflowRunsView.ByWorkflowAndStatus(workflowId, status));
  }

  @Get("/{workflowId}/stats")
  public WorkflowStats stats(String workflowId) {
    var runs = componentClient.forView()
        .method(WorkflowRunsView::byWorkflow)
        .invoke(workflowId)
        .runs();
    return WorkflowStatsCalculator.compute(runs);
  }
}
