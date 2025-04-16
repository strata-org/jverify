package com.aws.jverify.generated;

// Generated StringLiteralExpr.java:
// Generated from C# class
public class StringLiteralExpr extends LiteralExpr {
  private final Boolean isVerbatim;

  public StringLiteralExpr(IOrigin origin, Object value, Boolean isVerbatim) {
    super(origin, value);
    this.isVerbatim = isVerbatim;
  }

  public Boolean getIsVerbatim() {
    return this.isVerbatim;
  }
}
