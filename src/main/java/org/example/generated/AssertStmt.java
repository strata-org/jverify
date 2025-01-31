package org.example.generated;

// Generated AssertStmt.java:
// Generated from C# class
public class AssertStmt extends PredicateStmt {
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
