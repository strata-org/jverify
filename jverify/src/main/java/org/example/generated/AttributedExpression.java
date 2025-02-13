package org.example.generated;

// Generated AttributedExpression.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class AttributedExpression {
  private final Expression e;

  @Nullable
  private final AssertLabel label;

  @Nullable
  private final Attributes attributes;

  public AttributedExpression(Expression e, AssertLabel label, Attributes attributes) {
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
