package org.example.generated;

// Generated FrameExpression.java:
// Generated from C# class
public class FrameExpression extends NodeWithComputedRange {
  private final Expression e;

  private final String fieldName;

  public FrameExpression(SourceOrigin origin, Expression e, String fieldName) {
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
