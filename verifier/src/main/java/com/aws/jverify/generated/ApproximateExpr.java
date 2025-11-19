package com.aws.jverify.generated;

// Generated ApproximateExpr.java:
// Generated from C# class
public class ApproximateExpr extends ConcreteSyntaxExpression {
  private final Expression expr;

  public ApproximateExpr(IOrigin origin, Expression expr) {
    super(origin);
    this.expr = expr;
  }

  public Expression getExpr() {
    return this.expr;
  }
}
