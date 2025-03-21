package com.aws.jverify.generated;

// Generated ProduceStmt.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class ProduceStmt extends Statement {
  @Nullable
  private final List<AssignmentRhs> rhss;

  public ProduceStmt(IOrigin origin, Attributes attributes, List<AssignmentRhs> rhss) {
    super(origin, attributes);
    this.rhss = rhss;
  }

  public List<AssignmentRhs> getRhss() {
    return this.rhss;
  }
}
