package io.akka.voltagent.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** An in-process registry of workflow definitions — SPEC-001 §2. Holds definitions only,
 * never run history; matches the source's own registry, which is a process-local map of
 * definitions and does not itself hold history either (question-log row 6). */
public class WorkflowRegistry {

  private final Map<String, WorkflowDefinition> workflows = new LinkedHashMap<>();

  public void register(WorkflowDefinition workflow) {
    workflows.put(workflow.id(), workflow);
  }

  public WorkflowDefinition get(String id) {
    return workflows.get(id);
  }
}
