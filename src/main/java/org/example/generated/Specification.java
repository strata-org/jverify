package org.example.generated;

// Generated Specification.java:
// Generated from C# class
import java.util.List;

public class Specification<T extends Node> extends NodeWithComputedRange {
  private final List<T> expressions;

  private final Attributes attributes;

  public Specification(SourceOrigin origin, List<T> expressions, Attributes attributes) {
    super(origin);
    this.expressions = expressions;
    this.attributes = attributes;
  }

  public List<T> getExpressions() {
    return this.expressions;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
