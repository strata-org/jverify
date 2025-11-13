package com.aws.jverify.generated;

public class ApproximateExpr extends Expression {
  private final Expression expr;

  public ApproximateExpr(IOrigin origin, Expression expr) {
    super(origin);
    this.expr = expr;
  }

  public Expression getExpr() {
    return expr;
  }
}
