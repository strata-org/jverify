package org.example.generated;

// Generated TopLevelDecl.java:
// Generated from C# class
import java.util.List;

public class TopLevelDecl extends Declaration {
  private final List<TypeParameter> typeArgs;

  public TopLevelDecl(SourceOrigin origin, Name name, Attributes attributes,
      List<TypeParameter> typeArgs) {
    super(origin, name, attributes);
    this.typeArgs = typeArgs;
  }

  public List<TypeParameter> getTypeArgs() {
    return this.typeArgs;
  }
}
