package org.example.generated;

// Generated ClassDecl.java:
// Generated from C# class
import java.util.List;

public class ClassDecl extends ClassLikeDecl {
  private final Boolean isRefining;

  public ClassDecl(SourceOrigin origin, Name name, Attributes attributes,
      List<TypeParameter> typeArgs, List<MemberDecl> members, List<Type> traits,
      Boolean isRefining) {
    super(origin, name, attributes, typeArgs, members, traits);
    this.isRefining = isRefining;
  }

  public Boolean getIsRefining() {
    return this.isRefining;
  }
}
