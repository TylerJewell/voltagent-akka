package io.akka.voltagent.application;

import java.util.List;

/** A workflow definition — SPEC-001 §2. Distinct from a run: this is created once and
 * held in the {@link WorkflowRegistry}, never persisted itself. */
public record WorkflowDefinition(String id, String name, String purpose, List<Step> steps) {

  private static final String DEFAULT_PURPOSE = "No purpose provided";

  public WorkflowDefinition(String id, String name, List<Step> steps) {
    this(id, name, DEFAULT_PURPOSE, steps);
  }
}
