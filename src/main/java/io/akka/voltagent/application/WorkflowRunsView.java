package io.akka.voltagent.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.akka.voltagent.domain.RunStatus;
import io.akka.voltagent.domain.WorkflowRunEvent;
import java.util.List;

/** Runs, listable and filterable by workflow and status — SPEC-001 §3 rules 9-10. Full
 * per-step detail is read from {@link WorkflowRunEntity} directly by run id; this view is
 * the index across many runs that a single entity cannot answer for itself. */
@Component(id = "workflow-runs")
public class WorkflowRunsView extends View {

  public record RunSummary(String id, String workflowId, String workflowName, String status,
      long startTimeMillis, long endTimeMillis) {}

  public record Runs(List<RunSummary> runs) {}

  public record ByWorkflowAndStatus(String workflowId, String status) {}

  @Consume.FromEventSourcedEntity(WorkflowRunEntity.class)
  public static class RunsUpdater extends TableUpdater<RunSummary> {

    public Effect<RunSummary> onEvent(WorkflowRunEvent event) {
      String id = updateContext().eventSubject().get();
      RunSummary current = rowState();
      return switch (event) {
        case WorkflowRunEvent.RunStarted e -> effects().updateRow(new RunSummary(id,
            e.workflowId(), e.workflowName(), RunStatus.RUNNING.name(),
            e.startTime().toEpochMilli(), 0));
        case WorkflowRunEvent.StepStarted e -> effects().updateRow(current);
        case WorkflowRunEvent.StepSucceeded e -> effects().updateRow(current);
        case WorkflowRunEvent.StepSkipped e -> effects().updateRow(current);
        case WorkflowRunEvent.StepFailed e -> effects().updateRow(current);
        case WorkflowRunEvent.RunCompleted e -> effects().updateRow(new RunSummary(id,
            current.workflowId(), current.workflowName(), RunStatus.COMPLETED.name(),
            current.startTimeMillis(), e.endTime().toEpochMilli()));
        case WorkflowRunEvent.RunFailed e -> effects().updateRow(new RunSummary(id,
            current.workflowId(), current.workflowName(), RunStatus.ERROR.name(),
            current.startTimeMillis(), e.endTime().toEpochMilli()));
      };
    }
  }

  @Query("SELECT * AS runs FROM workflow_runs WHERE workflowId = :workflowId ORDER BY startTimeMillis DESC")
  public QueryEffect<Runs> byWorkflow(String workflowId) {
    return queryResult();
  }

  @Query("""
      SELECT * AS runs FROM workflow_runs
      WHERE workflowId = :workflowId AND status = :status
      ORDER BY startTimeMillis DESC
      """)
  public QueryEffect<Runs> byWorkflowAndStatus(ByWorkflowAndStatus params) {
    return queryResult();
  }
}
