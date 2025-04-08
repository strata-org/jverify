package com.aws.jverify.generated;

// Generated MultiSetFormingExpr.java:
// Generated from C# class
public class MultiSetFormingExpr extends Expression {
  private final Expression e;

  public MultiSetFormingExpr(IOrigin origin, Expression e) {
    super(origin);
    this.e = e;
  }

  public Expression getE() {
    return this.e;
  }
}
