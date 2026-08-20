package io.akka.voltagent.domain;

import java.time.Instant;

/** Per-workflow statistics, computed from the actual persisted run history — SPEC-001 §4
 * OD-2. The source's equivalent getter is a stub that always returns zero. */
public record WorkflowStats(
    long totalExecutions,
    long successfulExecutions,
    long failedExecutions,
    double averageExecutionTimeMillis,
    Instant lastExecutionTime) {

  public static WorkflowStats empty() {
    return new WorkflowStats(0, 0, 0, 0, null);
  }
}
