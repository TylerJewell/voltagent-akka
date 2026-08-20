package io.akka.voltagent.application;

import io.akka.voltagent.domain.WorkflowStats;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Computes {@link WorkflowStats} from a workflow's actual persisted run history —
 * SPEC-001 §3 rule 11, §4 OD-2. The source's equivalent, {@code getWorkflowStats()},
 * is a stub that always returns zero for every field (question-log row 12); this
 * reads the real numbers back from {@link WorkflowRunsView}. */
public final class WorkflowStatsCalculator {

  private WorkflowStatsCalculator() {}

  public static WorkflowStats compute(List<WorkflowRunsView.RunSummary> runs) {
    if (runs.isEmpty()) {
      return WorkflowStats.empty();
    }

    long total = runs.size();
    long successful = runs.stream().filter(r -> "COMPLETED".equals(r.status())).count();
    long failed = runs.stream().filter(r -> "ERROR".equals(r.status())).count();

    double averageMillis = runs.stream()
        .filter(r -> r.endTimeMillis() > 0)
        .mapToLong(r -> r.endTimeMillis() - r.startTimeMillis())
        .average()
        .orElse(0);

    Instant last = runs.stream()
        .map(r -> Instant.ofEpochMilli(r.startTimeMillis()))
        .max(Comparator.naturalOrder())
        .orElse(null);

    return new WorkflowStats(total, successful, failed, averageMillis, last);
  }
}
