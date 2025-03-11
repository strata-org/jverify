package com.aws.jverify.generated;

// Generated FrameExpression.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class FrameExpression extends NodeWithComputedRange {
  private final Expression e;

  @Nullable
  private final String fieldName;

  public FrameExpression(IOrigin origin, Expression e, String fieldName) {
    super(origin);
    this.e = e;
    this.fieldName = fieldName;
  }

  public Expression getE() {
    return this.e;
  }

  public String getFieldName() {
    return this.fieldName;
  }
}
