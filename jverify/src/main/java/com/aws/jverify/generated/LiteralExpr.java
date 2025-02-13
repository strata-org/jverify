package com.aws.jverify.generated;

// Generated LiteralExpr.java:
// Generated from C# class
public class LiteralExpr extends Expression {
  private final Object value;

  public LiteralExpr(SourceOrigin origin, Object value) {
    super(origin);
    this.value = value;
  }

  public Object getValue() {
    return this.value;
  }
}
