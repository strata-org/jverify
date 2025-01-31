package org.example.generated;

// Generated LiteralModuleDecl.java:
// Generated from C# class
import java.util.List;

public class LiteralModuleDecl extends ModuleDecl {
  private final ModuleDefinition moduleDef;

  public LiteralModuleDecl(SourceOrigin origin, Name name, Attributes attributes,
      List<TypeParameter> typeArgs, String cloneId, ModuleDefinition moduleDef) {
    super(origin, name, attributes, typeArgs, cloneId);
    this.moduleDef = moduleDef;
  }

  public ModuleDefinition getModuleDef() {
    return this.moduleDef;
  }
}
