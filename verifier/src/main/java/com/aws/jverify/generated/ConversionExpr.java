package com.aws.jverify.generated;

// Generated ConversionExpr.java:
// Generated from C# class
public class ConversionExpr extends TypeUnaryExpr {
  private final String messagePrefix;

  public ConversionExpr(IOrigin origin, Expression e, Type toType, String messagePrefix) {
    super(origin, e, toType);
    this.messagePrefix = messagePrefix;
  }

  public String getMessagePrefix() {
    return this.messagePrefix;
  }
}
