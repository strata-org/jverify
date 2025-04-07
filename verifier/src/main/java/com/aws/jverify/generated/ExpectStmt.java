package com.aws.jverify.generated;

// Generated ExpectStmt.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class ExpectStmt extends PredicateStmt {
  @Nullable
  private final Expression message;

  public ExpectStmt(IOrigin origin, Attributes attributes, Expression expr, Expression message) {
    super(origin, attributes, expr);
    this.message = message;
  }

  public Expression getMessage() {
    return this.message;
  }
}
