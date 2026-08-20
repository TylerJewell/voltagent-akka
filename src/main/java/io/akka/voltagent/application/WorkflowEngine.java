package io.akka.voltagent.application;

import akka.javasdk.client.ComponentClient;
import io.akka.voltagent.domain.RunStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs a {@link WorkflowDefinition} against an input, recording every step in a
 * {@link WorkflowRunEntity} as it goes — SPEC-001 §3. The engine itself holds no state; it
 * only interprets a definition's steps and drives the entity that owns the run's history. */
public class WorkflowEngine {

  private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

  private final ComponentClient componentClient;

  public WorkflowEngine(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record RunResult(String executionId, RunStatus status, Object output, String error) {}

  /** A step's outcome before it is recorded: its data, and whether a {@code when} step
   * skipped its nested step (SPEC-001 §3 rule 4). */
  private record StepOutcome(Object data, boolean skipped) {}

  public RunResult run(WorkflowDefinition definition, String executionId, Object input) {
    String id = executionId != null ? executionId : UUID.randomUUID().toString();
    var run = componentClient.forEventSourcedEntity(id);

    run.method(WorkflowRunEntity::start)
        .invoke(new WorkflowRunEntity.Start(definition.id(), definition.name(), input));

    Object data = input;
    for (int index = 0; index < definition.steps().size(); index++) {
      Step step = definition.steps().get(index);
      run.method(WorkflowRunEntity::stepStarted)
          .invoke(new WorkflowRunEntity.StepStarted(index, step.id(), step.id(), step.kind(),
              data));

      try {
        StepOutcome outcome = dispatch(step, new StepContext(data));
        if (outcome.skipped()) {
          run.method(WorkflowRunEntity::stepSkipped)
              .invoke(new WorkflowRunEntity.StepOutcome(index, outcome.data()));
        } else {
          run.method(WorkflowRunEntity::stepSucceeded)
              .invoke(new WorkflowRunEntity.StepOutcome(index, outcome.data()));
        }
        data = outcome.data();
      } catch (Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        run.method(WorkflowRunEntity::stepFailed)
            .invoke(new WorkflowRunEntity.StepError(index, message));
        run.method(WorkflowRunEntity::failRun).invoke(message);
        return new RunResult(id, RunStatus.ERROR, null, message);
      }
    }

    run.method(WorkflowRunEntity::completeRun).invoke(data);
    return new RunResult(id, RunStatus.COMPLETED, data, null);
  }

  private StepOutcome dispatch(Step step, StepContext context) throws Exception {
    return switch (step) {
      case Step.Func s -> new StepOutcome(s.execute().apply(context), false);

      case Step.When s -> {
        if (s.condition().test(context)) {
          StepOutcome nested = dispatch(s.step(), context);
          yield new StepOutcome(nested.data(), false);
        }
        yield new StepOutcome(context.data(), true);
      }

      case Step.All s -> new StepOutcome(runAll(s, context), false);

      case Step.Tap s -> {
        try {
          s.execute().accept(context);
        } catch (Exception e) {
          log.warn("tap step {} threw, ignored", s.id(), e);
        }
        yield new StepOutcome(context.data(), false);
      }

      case Step.Sleep s -> {
        Thread.sleep(Math.max(0, s.durationMillis()));
        yield new StepOutcome(context.data(), false);
      }
    };
  }

  /** Every sub-step runs, all of them are waited for, and results come back in the
   * sub-steps' original order — SPEC-001 §3 rule 5, question-log row 4. On failure, the
   * lowest-indexed sub-step's error is the one that surfaces — question-log row 5. */
  private List<Object> runAll(Step.All all, StepContext context) throws Exception {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<CompletableFuture<Object>> futures = new ArrayList<>();
      for (Step sub : all.steps()) {
        futures.add(CompletableFuture.supplyAsync(() -> {
          try {
            return dispatch(sub, context).data();
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        }, executor));
      }

      Object[] results = new Object[futures.size()];
      Exception firstError = null;
      for (int i = 0; i < futures.size(); i++) {
        try {
          results[i] = futures.get(i).join();
        } catch (CompletionException e) {
          if (firstError == null) {
            firstError = e.getCause() instanceof Exception cause ? cause : e;
          }
        }
      }
      if (firstError != null) {
        throw firstError;
      }
      return List.of(results);
    }
  }
}
