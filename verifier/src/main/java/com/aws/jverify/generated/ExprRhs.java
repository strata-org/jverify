package com.aws.jverify.generated;

// Generated ExprRhs.java:
// Generated from C# class
public class ExprRhs extends AssignmentRhs {
  private final Expression expr;

  public ExprRhs(IOrigin origin, Attributes attributes, Expression expr) {
    super(origin, attributes);
    this.expr = expr;
  }

  public Expression getExpr() {
    return this.expr;
  }
}
