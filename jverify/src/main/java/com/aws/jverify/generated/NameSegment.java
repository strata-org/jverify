package com.aws.jverify.generated;

// Generated NameSegment.java:
// Generated from C# class
import java.util.List;

public class NameSegment extends ConcreteSyntaxExpression {
  private final String name;

  private final List<Type> optTypeArguments;

  public NameSegment(SourceOrigin origin, String name, List<Type> optTypeArguments) {
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
