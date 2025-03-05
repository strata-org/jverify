package com.aws.jverify.generated;

// Generated NegationExpression.java:
// Generated from C# class
public class NegationExpression extends ConcreteSyntaxExpression {
  private final Expression e;

  public NegationExpression(IOrigin origin, Expression e) {
    super(origin);
    this.e = e;
  }

  public Expression getE() {
    return this.e;
  }
}
