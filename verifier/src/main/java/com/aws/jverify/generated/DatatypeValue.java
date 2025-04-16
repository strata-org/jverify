package com.aws.jverify.generated;

// Generated DatatypeValue.java:
// Generated from C# class
public class DatatypeValue extends Expression {
  private final String datatypeName;

  private final String memberName;

  private final ActualBindings bindings;

  public DatatypeValue(IOrigin origin, String datatypeName, String memberName,
      ActualBindings bindings) {
    super(origin);
    this.datatypeName = datatypeName;
    this.memberName = memberName;
    this.bindings = bindings;
  }

  public String getDatatypeName() {
    return this.datatypeName;
  }

  public String getMemberName() {
    return this.memberName;
  }

  public ActualBindings getBindings() {
    return this.bindings;
  }
}
