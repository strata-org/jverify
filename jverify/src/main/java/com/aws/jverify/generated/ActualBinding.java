package com.aws.jverify.generated;

// Generated ActualBinding.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class ActualBinding extends NodeWithComputedRange {
  @Nullable
  private final SourceOrigin formalParameterName;

  private final Expression actual;

  private final Boolean isGhost;

  public ActualBinding(SourceOrigin origin, SourceOrigin formalParameterName, Expression actual,
      Boolean isGhost) {
    super(origin);
    this.formalParameterName = formalParameterName;
    this.actual = actual;
    this.isGhost = isGhost;
  }

  public SourceOrigin getFormalParameterName() {
    return this.formalParameterName;
  }

  public Expression getActual() {
    return this.actual;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
