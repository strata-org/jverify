package com.aws.jverify.generated;

// Generated ParensExpression.java:
// Generated from C# class
public class ParensExpression extends ConcreteSyntaxExpression {
  private final Expression e;

  public ParensExpression(IOrigin origin, Expression e) {
    super(origin);
    this.e = e;
  }

  public Expression getE() {
    return this.e;
  }
}
