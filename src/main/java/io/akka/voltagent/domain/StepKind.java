package io.akka.voltagent.domain;

/** The five ported step kinds — SPEC-001 §1. */
public enum StepKind {
  FUNC,
  WHEN,
  ALL,
  TAP,
  SLEEP
}
