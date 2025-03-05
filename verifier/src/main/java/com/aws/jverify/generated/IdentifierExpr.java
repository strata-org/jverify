package com.aws.jverify.generated;

// Generated IdentifierExpr.java:
// Generated from C# class
public class IdentifierExpr extends Expression {
  private final String name;

  public IdentifierExpr(IOrigin origin, String name) {
    super(origin);
    this.name = name;
  }

  public String getName() {
    return this.name;
  }
}
