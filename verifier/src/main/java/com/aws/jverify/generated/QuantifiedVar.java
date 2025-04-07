package com.aws.jverify.generated;

// Generated QuantifiedVar.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class QuantifiedVar extends BoundVar {
  @Nullable
  private final Expression domain;

  @Nullable
  private final Expression range;

  public QuantifiedVar(IOrigin origin, Name nameNode, Type syntacticType, Boolean isGhost,
      Expression domain, Expression range) {
    super(origin, nameNode, syntacticType, isGhost);
    this.domain = domain;
    this.range = range;
  }

  public Expression getDomain() {
    return this.domain;
  }

  public Expression getRange() {
    return this.range;
  }
}
