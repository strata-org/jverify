package org.example.generated;

// Generated LiteralModuleDecl.java:
// Generated from C# class
public class LiteralModuleDecl extends ModuleDecl {
  private final ModuleDefinition moduleDef;

  public LiteralModuleDecl(SourceOrigin origin, Name nameNode, Attributes attributes,
      String cloneId, ModuleDefinition moduleDef) {
    super(origin, nameNode, attributes, cloneId);
    this.moduleDef = moduleDef;
  }

  public ModuleDefinition getModuleDef() {
    return this.moduleDef;
  }
}
