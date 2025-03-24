package com.aws.jverify.generated;

// Generated MemberDecl.java:
// Generated from C# class
public abstract class MemberDecl extends Declaration {
  private final Boolean isGhost;

  public MemberDecl(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost) {
    super(origin, nameNode, attributes);
    this.isGhost = isGhost;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
