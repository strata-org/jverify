package org.example.generated;

// Generated ModuleDecl.java:
// Generated from C# class
import java.util.List;

public class ModuleDecl extends TopLevelDecl {
  private final String cloneId;

  public ModuleDecl(SourceOrigin origin, Name name, Attributes attributes,
      List<TypeParameter> typeArgs, String cloneId) {
    super(origin, name, attributes, typeArgs);
    this.cloneId = cloneId;
  }

  public String getCloneId() {
    return this.cloneId;
  }
}
