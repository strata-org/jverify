package com.aws.jverify.generated;

import org.checkerframework.checker.nullness.qual.Nullable;

public class DecimalLiteralExpr extends LiteralExpr {
  @Nullable
  private BigFloat resolvedFloatValue;

  public DecimalLiteralExpr(IOrigin origin, BigDec value) {
    super(origin, value);
  }

  public DecimalLiteralExpr(IOrigin origin, BigDec value, BigFloat resolvedFloatValue) {
    super(origin, value);
    this.resolvedFloatValue = resolvedFloatValue;
  }

  @Nullable
  public BigFloat getResolvedFloatValue() {
    return resolvedFloatValue;
  }

  public void setResolvedFloatValue(BigFloat value) {
    this.resolvedFloatValue = value;
  }
}
