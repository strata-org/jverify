package com.aws.jverify.generated;

// Generated UserDefinedType.java:
// Generated from C# class
public class UserDefinedType extends NonProxyType {
  private final Expression namePath;

  public UserDefinedType(IOrigin origin, Expression namePath) {
    super(origin);
    this.namePath = namePath;
  }

  public Expression getNamePath() {
    return this.namePath;
  }
}
