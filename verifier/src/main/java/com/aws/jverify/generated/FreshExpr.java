package com.aws.jverify.generated;

// Generated FreshExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class FreshExpr extends UnaryOpExpr {
  @Nullable
  private final String at;

  public FreshExpr(IOrigin origin, Expression e, UnaryOpExprOpcode op, String at) {
    super(origin, e, op);
    this.at = at;
  }

  public String getAt() {
    return this.at;
  }
}
