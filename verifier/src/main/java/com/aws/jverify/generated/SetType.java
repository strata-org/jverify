package com.aws.jverify.generated;

// Generated SetType.java:
// Generated from C# class
import java.util.List;

public class SetType extends CollectionType {
  private final Boolean finite;

  public SetType(IOrigin origin, List<Type> typeArgs, Boolean finite) {
    super(origin, typeArgs);
    this.finite = finite;
  }

  public Boolean getFinite() {
    return this.finite;
  }
}
