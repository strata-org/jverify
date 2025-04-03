package com.aws.jverify.generated;

// Generated TokenRange.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class TokenRange {
  private final Token startToken;

  @Nullable
  private final Token endToken;

  public TokenRange(Token startToken, Token endToken) {
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
