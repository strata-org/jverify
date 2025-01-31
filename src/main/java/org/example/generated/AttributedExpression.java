package org.example.generated;

// Generated AttributedExpression.java:
// Generated from C# class
public class AttributedExpression extends NodeWithComputedRange {
  private final Expression e;

  private final AssertLabel label;

  private final Attributes attributes;

  public AttributedExpression(SourceOrigin origin, Expression e, AssertLabel label,
      Attributes attributes) {
    super(origin);
    this.e = e;
    this.label = label;
    this.attributes = attributes;
  }

  public Expression getE() {
    return this.e;
  }

  public AssertLabel getLabel() {
    return this.label;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
