package com.aws.jverify.generated;

// Generated LiteralExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class LiteralExpr extends Expression {
  @Nullable
  private final Object value;

  public LiteralExpr(IOrigin origin, Object value) {
    super(origin);
    this.value = value;
  }

  public Object getValue() {
    return this.value;
  }
}
