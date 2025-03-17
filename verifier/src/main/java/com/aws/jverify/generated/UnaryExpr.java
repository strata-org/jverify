package com.aws.jverify.generated;

// Generated UnaryExpr.java:
// Generated from C# class
public abstract class UnaryExpr extends Expression {
  private final Expression e;

  public UnaryExpr(IOrigin origin, Expression e) {
    super(origin);
    this.e = e;
  }

  public Expression getE() {
    return this.e;
  }
}
