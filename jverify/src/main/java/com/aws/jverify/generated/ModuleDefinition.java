package com.aws.jverify.generated;

// Generated ModuleDefinition.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ModuleDefinition extends RangeNode {
  private final Name nameNode;

  private final List<SourceOrigin> prefixIds;

  private final ModuleKindEnum moduleKind;

  @Nullable
  private final Implements implements1;

  @Nullable
  private final Attributes attributes;

  private final List<TopLevelDecl> sourceDecls;

  public ModuleDefinition(SourceOrigin origin, Name nameNode, List<SourceOrigin> prefixIds,
      ModuleKindEnum moduleKind, Implements implements1, Attributes attributes,
      List<TopLevelDecl> sourceDecls) {
    super(origin);
    this.nameNode = nameNode;
    this.prefixIds = prefixIds;
    this.moduleKind = moduleKind;
    this.implements1 = implements1;
    this.attributes = attributes;
    this.sourceDecls = sourceDecls;
  }

  public Name getNameNode() {
    return this.nameNode;
  }

  public List<SourceOrigin> getPrefixIds() {
    return this.prefixIds;
  }

  public ModuleKindEnum getModuleKind() {
    return this.moduleKind;
  }

  public Implements getImplements1() {
    return this.implements1;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }

  public List<TopLevelDecl> getSourceDecls() {
    return this.sourceDecls;
  }
}
