package com.aws.jverify.generated;

// Generated IdPattern.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class IdPattern extends ExtendedPattern {
  private final String id;

  @Nullable
  private final Type syntacticType;

  @Nullable
  private final List<ExtendedPattern> arguments;

  private final Boolean hasParenthesis;

  public IdPattern(IOrigin origin, Boolean isGhost, String id, Type syntacticType,
      List<ExtendedPattern> arguments, Boolean hasParenthesis) {
    super(origin, isGhost);
    this.id = id;
    this.syntacticType = syntacticType;
    this.arguments = arguments;
    this.hasParenthesis = hasParenthesis;
  }

  public String getId() {
    return this.id;
  }

  public Type getSyntacticType() {
    return this.syntacticType;
  }

  public List<ExtendedPattern> getArguments() {
    return this.arguments;
  }

  public Boolean getHasParenthesis() {
    return this.hasParenthesis;
  }
}
