package com.aws.jverify.generated;

// Generated TypeParameter.java:
// Generated from C# class
import java.util.List;

public class TypeParameter extends Declaration {
  private final TPVarianceSyntax varianceSyntax;

  private final TypeParameterCharacteristics characteristics;

  private final List<Type> typeBounds;

  public TypeParameter(SourceOrigin origin, Name nameNode, Attributes attributes,
      TPVarianceSyntax varianceSyntax, TypeParameterCharacteristics characteristics,
      List<Type> typeBounds) {
    super(origin, nameNode, attributes);
    this.varianceSyntax = varianceSyntax;
    this.characteristics = characteristics;
    this.typeBounds = typeBounds;
  }

  public TPVarianceSyntax getVarianceSyntax() {
    return this.varianceSyntax;
  }

  public TypeParameterCharacteristics getCharacteristics() {
    return this.characteristics;
  }

  public List<Type> getTypeBounds() {
    return this.typeBounds;
  }
}
