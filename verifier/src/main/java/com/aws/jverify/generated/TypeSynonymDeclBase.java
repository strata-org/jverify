package com.aws.jverify.generated;

// Generated TypeSynonymDeclBase.java:
// Generated from C# class
import java.util.List;

public abstract class TypeSynonymDeclBase extends TopLevelDecl {
  private final TypeParameterCharacteristics characteristics;

  public TypeSynonymDeclBase(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, TypeParameterCharacteristics characteristics) {
    super(origin, nameNode, attributes, typeArgs);
    this.characteristics = characteristics;
  }

  public TypeParameterCharacteristics getCharacteristics() {
    return this.characteristics;
  }
}
