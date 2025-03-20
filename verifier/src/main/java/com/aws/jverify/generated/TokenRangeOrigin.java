package com.aws.jverify.generated;

// Generated TokenRangeOrigin.java:
// Generated from C# class
public class TokenRangeOrigin extends IOrigin {
  private final Token startToken;

  private final Token endToken;

  public TokenRangeOrigin(Token startToken, Token endToken) {
    super();
    this.startToken = startToken;
    this.endToken = endToken;
  }

  public Token getStartToken() {
    return this.startToken;
  }

  public Token getEndToken() {
    return this.endToken;
  }
}
