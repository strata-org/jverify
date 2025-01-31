package org.example.generated;

// Generated ModuleDecl.java:
// Generated from C# class
import java.util.List;

public class ModuleDecl extends TopLevelDecl {
  private final Boolean opened;

  private final String cloneId;

  public ModuleDecl(SourceOrigin origin, Name name, Attributes attributes, Boolean isRefining,
      List<TypeParameter> typeArgs, Boolean opened, String cloneId) {
    super(origin, name, attributes, isRefining, typeArgs);
    this.opened = opened;
    this.cloneId = cloneId;
  }

  public Boolean getOpened() {
    return this.opened;
  }

  public String getCloneId() {
    return this.cloneId;
  }
}
