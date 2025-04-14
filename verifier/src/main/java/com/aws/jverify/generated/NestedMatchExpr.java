package com.aws.jverify.generated;

// Generated NestedMatchExpr.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NestedMatchExpr extends Expression {
  private final Expression source;

  private final List<NestedMatchCaseExpr> cases;

  private final Boolean usesOptionalBraces;

  @Nullable
  private final Attributes attributes;

  public NestedMatchExpr(IOrigin origin, Expression source, List<NestedMatchCaseExpr> cases,
      Boolean usesOptionalBraces, Attributes attributes) {
    super(origin);
    this.source = source;
    this.cases = cases;
    this.usesOptionalBraces = usesOptionalBraces;
    this.attributes = attributes;
  }

  public Expression getSource() {
    return this.source;
  }

  public List<NestedMatchCaseExpr> getCases() {
    return this.cases;
  }

  public Boolean getUsesOptionalBraces() {
    return this.usesOptionalBraces;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
