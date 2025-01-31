package org.example.generated;

// Generated TopLevelDeclWithMembers.java:
// Generated from C# class
import java.util.List;

public class TopLevelDeclWithMembers extends TopLevelDecl {
  private final List<MemberDecl> members;

  private final List<Type> traits;

  public TopLevelDeclWithMembers(SourceOrigin origin, Name name, Attributes attributes,
      List<TypeParameter> typeArgs, List<MemberDecl> members, List<Type> traits) {
    super(origin, name, attributes, typeArgs);
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
