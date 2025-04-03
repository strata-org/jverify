package com.aws.jverify.generated;

// Generated SetDisplayExpr.java:
// Generated from C# class
import java.util.List;

public class SetDisplayExpr extends DisplayExpression {
  private final Boolean finite;

  public SetDisplayExpr(IOrigin origin, List<Expression> elements, Boolean finite) {
    super(origin, elements);
    this.finite = finite;
  }

  public Boolean getFinite() {
    return this.finite;
  }
}
