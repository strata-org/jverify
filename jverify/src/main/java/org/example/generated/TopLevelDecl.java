package org.example.generated;

// Generated TopLevelDecl.java:
// Generated from C# class
import java.util.List;

public abstract class TopLevelDecl extends Declaration {
  private final List<TypeParameter> typeArgs;

  public TopLevelDecl(SourceOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs) {
    super(origin, nameNode, attributes);
    this.typeArgs = typeArgs;
  }

  public List<TypeParameter> getTypeArgs() {
    return this.typeArgs;
  }
}
