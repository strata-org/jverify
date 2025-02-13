package com.aws.jverify.generated;

// Generated AssignStatement.java:
// Generated from C# class
import java.util.List;

public class AssignStatement extends ConcreteAssignStatement {
  private final List<AssignmentRhs> rhss;

  private final Boolean canMutateKnownState;

  public AssignStatement(SourceOrigin origin, Attributes attributes, List<Expression> lhss,
      List<AssignmentRhs> rhss, Boolean canMutateKnownState) {
    super(origin, attributes, lhss);
    this.rhss = rhss;
    this.canMutateKnownState = canMutateKnownState;
  }

  public List<AssignmentRhs> getRhss() {
    return this.rhss;
  }

  public Boolean getCanMutateKnownState() {
    return this.canMutateKnownState;
  }
}
