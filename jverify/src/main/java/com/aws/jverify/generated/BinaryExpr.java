package com.aws.jverify.generated;

// Generated BinaryExpr.java:
// Generated from C# class
public class BinaryExpr extends Expression {
  private final BinaryExprOpcode op;

  private final Expression e0;

  private final Expression e1;

  public BinaryExpr(IOrigin origin, BinaryExprOpcode op, Expression e0, Expression e1) {
    super(origin);
    this.op = op;
    this.e0 = e0;
    this.e1 = e1;
  }

  public BinaryExprOpcode getOp() {
    return this.op;
  }

  public Expression getE0() {
    return this.e0;
  }

  public Expression getE1() {
    return this.e1;
  }
}
