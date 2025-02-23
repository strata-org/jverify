package com.aws.jverify.generated;

// Generated SourceOrigin.java:
// Generated from C# class
public class SourceOrigin extends IOrigin {
  private final Token startToken;

  private final Token endToken;

  private final Token center;

  public SourceOrigin(Token startToken, Token endToken, Token center) {
    this.startToken = startToken;
    this.endToken = endToken;
    this.center = center;
  }

  public Token getStartToken() {
    return this.startToken;
  }

  public Token getEndToken() {
    return this.endToken;
  }

  public Token getCenter() {
    return this.center;
  }
}
