package org.example.generated;

// Generated ModuleDecl.java:
// Generated from C# class
public abstract class ModuleDecl extends Declaration {
  private final String cloneId;

  public ModuleDecl(SourceOrigin origin, Name nameNode, Attributes attributes, String cloneId) {
    super(origin, nameNode, attributes);
    this.cloneId = cloneId;
  }

  public String getCloneId() {
    return this.cloneId;
  }
}
