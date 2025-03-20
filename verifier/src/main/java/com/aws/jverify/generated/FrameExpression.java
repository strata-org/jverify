package com.aws.jverify.generated;

// Generated FrameExpression.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class FrameExpression extends NodeWithOrigin {
  private final Expression originalExpression;

  @Nullable
  private final String fieldName;

  public FrameExpression(IOrigin origin, Expression originalExpression, String fieldName) {
    super(origin);
    this.originalExpression = originalExpression;
    this.fieldName = fieldName;
  }

  public Expression getOriginalExpression() {
    return this.originalExpression;
  }

  public String getFieldName() {
    return this.fieldName;
  }
}
