package org.example.generated;

// Generated AssertStmt.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class AssertStmt extends PredicateStmt {
  @Nullable
  private final AssertLabel label;

  public AssertStmt(SourceOrigin origin, Attributes attributes, Expression expr,
      AssertLabel label) {
    super(origin, attributes, expr);
    this.label = label;
  }

  public AssertLabel getLabel() {
    return this.label;
  }
}
