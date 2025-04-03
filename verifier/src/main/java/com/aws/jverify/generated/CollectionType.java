package com.aws.jverify.generated;

// Generated CollectionType.java:
// Generated from C# class
import java.util.List;

public abstract class CollectionType extends NonProxyType {
  private final List<Type> typeArgs;

  public CollectionType(IOrigin origin, List<Type> typeArgs) {
    super(origin);
    this.typeArgs = typeArgs;
  }

  public List<Type> getTypeArgs() {
    return this.typeArgs;
  }
}
