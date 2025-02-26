package com.aws.jverify.generated;

// Generated AssertStmt.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class AssertStmt extends PredicateStmt {
  @Nullable
  private final AssertLabel label;

  public AssertStmt(IOrigin origin, Attributes attributes, Expression expr, AssertLabel label) {
    super(origin, attributes, expr);
    this.label = label;
  }

  public AssertLabel getLabel() {
    return this.label;
  }
}
