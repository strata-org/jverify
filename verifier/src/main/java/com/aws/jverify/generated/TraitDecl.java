package com.aws.jverify.generated;

// Generated TraitDecl.java:
// Generated from C# class
import java.util.List;

public class TraitDecl extends ClassLikeDecl {
  private final Boolean isRefining;

  public TraitDecl(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, List<MemberDecl> members, List<Type> traits,
      Boolean isRefining) {
    super(origin, nameNode, attributes, typeArgs, members, traits);
    this.isRefining = isRefining;
  }

  public Boolean getIsRefining() {
    return this.isRefining;
  }
}
