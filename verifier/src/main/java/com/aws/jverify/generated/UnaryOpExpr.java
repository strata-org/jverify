package com.aws.jverify.generated;

// Generated UnaryOpExpr.java:
// Generated from C# class
public class UnaryOpExpr extends UnaryExpr {
  private final UnaryOpExprOpcode op;

  public UnaryOpExpr(IOrigin origin, Expression e, UnaryOpExprOpcode op) {
    super(origin, e);
    this.op = op;
  }

  public UnaryOpExprOpcode getOp() {
    return this.op;
  }
}
