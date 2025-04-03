package com.aws.jverify.generated;

// Generated MapType.java:
// Generated from C# class
import java.util.List;

public class MapType extends CollectionType {
  private final Boolean finite;

  public MapType(IOrigin origin, List<Type> typeArgs, Boolean finite) {
    super(origin, typeArgs);
    this.finite = finite;
  }

  public Boolean getFinite() {
    return this.finite;
  }
}
