package com.aws.jverify.generated;

// Generated DatatypeDecl.java:
// Generated from C# class
import java.util.List;

public abstract class DatatypeDecl extends TopLevelDeclWithMembers {
  private final List<DatatypeCtor> ctors;

  private final Boolean isRefining;

  public DatatypeDecl(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, List<MemberDecl> members, List<Type> traits,
      List<DatatypeCtor> ctors, Boolean isRefining) {
    super(origin, nameNode, attributes, typeArgs, members, traits);
    this.ctors = ctors;
    this.isRefining = isRefining;
  }

  public List<DatatypeCtor> getCtors() {
    return this.ctors;
  }

  public Boolean getIsRefining() {
    return this.isRefining;
  }
}
