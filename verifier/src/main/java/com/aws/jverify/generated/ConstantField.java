package com.aws.jverify.generated;

// Generated ConstantField.java:
// Generated from C# class
public class ConstantField extends Field {
  private final Expression rhs;

  private final Boolean hasStaticKeyword;

  private final Boolean isOpaque;

  public ConstantField(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      Type type, Expression rhs, Boolean hasStaticKeyword, Boolean isOpaque) {
    super(origin, nameNode, attributes, isGhost, type);
    this.rhs = rhs;
    this.hasStaticKeyword = hasStaticKeyword;
    this.isOpaque = isOpaque;
  }

  public Expression getRhs() {
    return this.rhs;
  }

  public Boolean getHasStaticKeyword() {
    return this.hasStaticKeyword;
  }

  public Boolean getIsOpaque() {
    return this.isOpaque;
  }
}
