package com.aws.jverify.generated;

// Generated TypeRhs.java:
// Generated from C# class
public class TypeRhs extends AssignmentRhs {
  private final Type path;

  private final ActualBindings arguments;

  public TypeRhs(IOrigin origin, Attributes attributes, Type path, ActualBindings arguments) {
    super(origin, attributes);
    this.path = path;
    this.arguments = arguments;
  }

  public Type getPath() {
    return this.path;
  }

  public ActualBindings getArguments() {
    return this.arguments;
  }
}
