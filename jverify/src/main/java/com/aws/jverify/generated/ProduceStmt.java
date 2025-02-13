package com.aws.jverify.generated;

// Generated ProduceStmt.java:
// Generated from C# class
import java.util.List;

public abstract class ProduceStmt extends Statement {
  private final List<AssignmentRhs> rhss;

  public ProduceStmt(SourceOrigin origin, Attributes attributes, List<AssignmentRhs> rhss) {
    super(origin, attributes);
    this.rhss = rhss;
  }

  public List<AssignmentRhs> getRhss() {
    return this.rhss;
  }
}
