package com.aws.jverify.generated;

// Generated QuantifiedVar.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class QuantifiedVar extends NodeWithOrigin {
  @Nullable
  private final Expression domain;

  @Nullable
  private final Expression range;

  public QuantifiedVar(IOrigin origin, Expression domain, Expression range) {
    super(origin);
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
