package com.aws.jverify.generated;

// Generated MapComprehension.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MapComprehension extends ComprehensionExpr {
  private final Boolean finite;

  @Nullable
  private final Expression termLeft;

  public MapComprehension(IOrigin origin, List<BoundVar> boundVars, Expression range,
      Expression term, Attributes attributes, Boolean finite, Expression termLeft) {
    super(origin, boundVars, range, term, attributes);
    this.finite = finite;
    this.termLeft = termLeft;
  }

  public Boolean getFinite() {
    return this.finite;
  }

  public Expression getTermLeft() {
    return this.termLeft;
  }
}
