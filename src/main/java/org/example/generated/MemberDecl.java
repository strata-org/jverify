package org.example.generated;

// Generated MemberDecl.java:
// Generated from C# class
public class MemberDecl extends Declaration {
  private final Boolean hasStaticKeyword;

  private final Boolean isGhost;

  public MemberDecl(SourceOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, Boolean isGhost) {
    super(origin, nameNode, attributes);
    this.hasStaticKeyword = hasStaticKeyword;
    this.isGhost = isGhost;
  }

  public Boolean getHasStaticKeyword() {
    return this.hasStaticKeyword;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
