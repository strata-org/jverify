package com.aws.jverify.generated;

// Generated ActualBinding.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class ActualBinding extends NodeWithoutOrigin {
  @Nullable
  private final IOrigin formalParameterName;

  private final Expression actual;

  private final Boolean isGhost;

  public ActualBinding(IOrigin formalParameterName, Expression actual, Boolean isGhost) {
    super();
    this.formalParameterName = formalParameterName;
    this.actual = actual;
    this.isGhost = isGhost;
  }

  public IOrigin getFormalParameterName() {
    return this.formalParameterName;
  }

  public Expression getActual() {
    return this.actual;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
