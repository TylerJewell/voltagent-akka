package io.akka.voltagent.application;

import io.akka.voltagent.domain.StepKind;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** A workflow definition's step — SPEC-001 §1. Not persisted: a step carries executable
 * behaviour, held only in the in-process {@link WorkflowRegistry}. */
public sealed interface Step {

  String id();

  StepKind kind();

  /** Run a function, replacing the run's data with whatever it returns — SPEC-001 §3.3. */
  record Func(String id, Function<StepContext, Object> execute) implements Step {
    @Override
    public StepKind kind() {
      return StepKind.FUNC;
    }
  }

  /** Run a nested step only if the condition holds — SPEC-001 §3.4. */
  record When(String id, Predicate<StepContext> condition, Step step) implements Step {
    @Override
    public StepKind kind() {
      return StepKind.WHEN;
    }
  }

  /** Run every sub-step, and wait for all of them regardless of failure — SPEC-001 §3.5. */
  record All(String id, List<Step> steps) implements Step {
    @Override
    public StepKind kind() {
      return StepKind.ALL;
    }
  }

  /** Run a side effect; never fails the run, never changes the data — SPEC-001 §3.6. */
  record Tap(String id, Consumer<StepContext> execute) implements Step {
    @Override
    public StepKind kind() {
      return StepKind.TAP;
    }
  }

  /** Pause for a duration, then leave the data unchanged — SPEC-001 §3.7. A negative
   * duration is clamped to zero, not an error. */
  record Sleep(String id, long durationMillis) implements Step {
    @Override
    public StepKind kind() {
      return StepKind.SLEEP;
    }
  }
}
