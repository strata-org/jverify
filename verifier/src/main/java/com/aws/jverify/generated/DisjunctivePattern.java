package com.aws.jverify.generated;

// Generated DisjunctivePattern.java:
// Generated from C# class
import java.util.List;

public class DisjunctivePattern extends ExtendedPattern {
  private final List<ExtendedPattern> alternatives;

  public DisjunctivePattern(IOrigin origin, Boolean isGhost, List<ExtendedPattern> alternatives) {
    super(origin, isGhost);
    this.alternatives = alternatives;
  }

  public List<ExtendedPattern> getAlternatives() {
    return this.alternatives;
  }
}
