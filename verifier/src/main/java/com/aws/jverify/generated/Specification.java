package com.aws.jverify.generated;

// Generated Specification.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Specification<T extends Node> extends NodeWithoutOrigin {
  @Nullable
  private final List<T> expressions;

  @Nullable
  private final Attributes attributes;

  public Specification(List<T> expressions, Attributes attributes) {
    super();
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
