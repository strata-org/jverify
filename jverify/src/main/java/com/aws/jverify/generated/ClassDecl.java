package com.aws.jverify.generated;

// Generated ClassDecl.java:
// Generated from C# class
import java.util.List;

public class ClassDecl extends ClassLikeDecl {
  private final Boolean isRefining;

  public ClassDecl(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, List<MemberDecl> members, List<Type> traits,
      Boolean isRefining) {
    super(origin, nameNode, attributes, typeArgs, members, traits);
    this.isRefining = isRefining;
  }

  public Boolean getIsRefining() {
    return this.isRefining;
  }
}
