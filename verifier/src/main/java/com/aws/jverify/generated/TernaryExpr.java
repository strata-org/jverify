package com.aws.jverify.generated;

// Generated TernaryExpr.java:
// Generated from C# class
public class TernaryExpr extends Expression {
  private final TernaryExprOpcode op;

  private final Expression e0;

  private final Expression e1;

  private final Expression e2;

  public TernaryExpr(IOrigin origin, TernaryExprOpcode op, Expression e0, Expression e1,
      Expression e2) {
    super(origin);
    this.op = op;
    this.e0 = e0;
    this.e1 = e1;
    this.e2 = e2;
  }

  public TernaryExprOpcode getOp() {
    return this.op;
  }

  public Expression getE0() {
    return this.e0;
  }

  public Expression getE1() {
    return this.e1;
  }

  public Expression getE2() {
    return this.e2;
  }
}
