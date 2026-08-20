package io.akka.voltagent.application;

/** What a step's function sees: the run's current data, as of just before this step. */
public record StepContext(Object data) {}
