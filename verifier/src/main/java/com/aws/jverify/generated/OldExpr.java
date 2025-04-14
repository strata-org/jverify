package com.aws.jverify.generated;

// Generated OldExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class OldExpr extends Expression {
  private final Expression expr;

  @Nullable
  private final String at;

  public OldExpr(IOrigin origin, Expression expr, String at) {
    super(origin);
    this.expr = expr;
    this.at = at;
  }

  public Expression getExpr() {
    return this.expr;
  }

  public String getAt() {
    return this.at;
  }
}
