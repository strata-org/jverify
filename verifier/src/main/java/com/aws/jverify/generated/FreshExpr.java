package com.aws.jverify.generated;

// Generated FreshExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class FreshExpr extends UnaryExpr {
  @Nullable
  private final String at;

  public FreshExpr(IOrigin origin, Expression e, String at) {
    super(origin, e);
    this.at = at;
  }

  public String getAt() {
    return this.at;
  }
}
