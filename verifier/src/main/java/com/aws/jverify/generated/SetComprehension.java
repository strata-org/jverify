package com.aws.jverify.generated;

// Generated SetComprehension.java:
// Generated from C# class
import java.util.List;

public class SetComprehension extends ComprehensionExpr {
  private final Boolean finite;

  public SetComprehension(IOrigin origin, List<BoundVar> boundVars, Expression range,
      Expression term, Attributes attributes, Boolean finite) {
    super(origin, boundVars, range, term, attributes);
    this.finite = finite;
  }

  public Boolean getFinite() {
    return this.finite;
  }
}
