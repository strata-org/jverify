package com.aws.jverify.generated;

// Generated LitPattern.java:
// Generated from C# class
public class LitPattern extends ExtendedPattern {
  private final Expression origLit;

  public LitPattern(IOrigin origin, Boolean isGhost, Expression origLit) {
    super(origin, isGhost);
    this.origLit = origLit;
  }

  public Expression getOrigLit() {
    return this.origLit;
  }
}
