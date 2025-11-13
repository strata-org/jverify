package com.aws.jverify.generated;

public class BigDec {
  private final double value;

  public BigDec(double value) {
    this.value = value;
  }

  public double getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}
