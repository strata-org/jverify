package com.aws.jverify.generated;

// Generated NameSegment.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NameSegment extends ConcreteSyntaxExpression {
  private final String name;

  @Nullable
  private final List<Type> optTypeArguments;

  public NameSegment(IOrigin origin, String name, List<Type> optTypeArguments) {
    super(origin);
    this.name = name;
    this.optTypeArguments = optTypeArguments;
  }

  public String getName() {
    return this.name;
  }

  public List<Type> getOptTypeArguments() {
    return this.optTypeArguments;
  }
}
