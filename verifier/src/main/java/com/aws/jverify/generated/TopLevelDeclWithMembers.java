package com.aws.jverify.generated;

// Generated TopLevelDeclWithMembers.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class TopLevelDeclWithMembers extends TopLevelDecl {
  private final List<MemberDecl> members;

  @Nullable
  private final List<Type> traits;

  public TopLevelDeclWithMembers(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, List<MemberDecl> members, List<Type> traits) {
    super(origin, nameNode, attributes, typeArgs);
    this.members = members;
    this.traits = traits;
  }

  public List<MemberDecl> getMembers() {
    return this.members;
  }

  public List<Type> getTraits() {
    return this.traits;
  }
}
