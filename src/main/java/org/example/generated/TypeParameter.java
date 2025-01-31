package org.example.generated;

// Generated TypeParameter.java:
// Generated from C# class
import java.util.List;

public class TypeParameter extends TopLevelDecl {
  private final TypeParameterTPVarianceSyntax varianceSyntax;

  private final TypeParameterTypeParameterCharacteristics characteristics;

  private final List<Type> typeBounds;

  public TypeParameter(SourceOrigin origin, Name name, Attributes attributes,
      List<TypeParameter> typeArgs, TypeParameterTPVarianceSyntax varianceSyntax,
      TypeParameterTypeParameterCharacteristics characteristics, List<Type> typeBounds) {
    super(origin, name, attributes, typeArgs);
    this.varianceSyntax = varianceSyntax;
    this.characteristics = characteristics;
    this.typeBounds = typeBounds;
  }

  public TypeParameterTPVarianceSyntax getVarianceSyntax() {
    return this.varianceSyntax;
  }

  public TypeParameterTypeParameterCharacteristics getCharacteristics() {
    return this.characteristics;
  }

  public List<Type> getTypeBounds() {
    return this.typeBounds;
  }
}
