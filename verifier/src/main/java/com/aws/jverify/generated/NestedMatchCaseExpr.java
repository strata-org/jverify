package com.aws.jverify.generated;

// Generated NestedMatchCaseExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class NestedMatchCaseExpr extends NestedMatchCase {
  private final Expression body;

  @Nullable
  private final Attributes attributes;

  public NestedMatchCaseExpr(IOrigin origin, ExtendedPattern pat, Expression body,
      Attributes attributes) {
    super(origin, pat);
    this.body = body;
    this.attributes = attributes;
  }

  public Expression getBody() {
    return this.body;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
