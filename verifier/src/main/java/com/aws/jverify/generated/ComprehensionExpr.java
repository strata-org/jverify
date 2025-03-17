package com.aws.jverify.generated;

// Generated ComprehensionExpr.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class ComprehensionExpr extends Expression {
  private final List<BoundVar> boundVars;

  @Nullable
  private final Expression range;

  private final Expression term;

  @Nullable
  private final Attributes attributes;

  public ComprehensionExpr(IOrigin origin, List<BoundVar> boundVars, Expression range,
      Expression term, Attributes attributes) {
    super(origin);
    this.boundVars = boundVars;
    this.range = range;
    this.term = term;
    this.attributes = attributes;
  }

  public List<BoundVar> getBoundVars() {
    return this.boundVars;
  }

  public Expression getRange() {
    return this.range;
  }

  public Expression getTerm() {
    return this.term;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
