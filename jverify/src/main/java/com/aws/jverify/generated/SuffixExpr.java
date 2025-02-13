package com.aws.jverify.generated;

// Generated SuffixExpr.java:
// Generated from C# class
public abstract class SuffixExpr extends ConcreteSyntaxExpression {
  private final Expression lhs;

  public SuffixExpr(SourceOrigin origin, Expression lhs) {
    super(origin);
    this.lhs = lhs;
  }

  public Expression getLhs() {
    return this.lhs;
  }
}
