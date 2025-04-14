package com.aws.jverify.generated;

// Generated BreakOrContinueStmt.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class BreakOrContinueStmt extends Statement {
  @Nullable
  private final Name targetLabel;

  private final int breakAndContinueCount;

  private final Boolean isContinue;

  public BreakOrContinueStmt(IOrigin origin, Attributes attributes, Name targetLabel,
      int breakAndContinueCount, Boolean isContinue) {
    super(origin, attributes);
    this.targetLabel = targetLabel;
    this.breakAndContinueCount = breakAndContinueCount;
    this.isContinue = isContinue;
  }

  public Name getTargetLabel() {
    return this.targetLabel;
  }

  public int getBreakAndContinueCount() {
    return this.breakAndContinueCount;
  }

  public Boolean getIsContinue() {
    return this.isContinue;
  }
}
