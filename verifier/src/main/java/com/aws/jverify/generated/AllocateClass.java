package com.aws.jverify.generated;

// Generated AllocateClass.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class AllocateClass extends TypeRhs {
  private final Type path;

  @Nullable
  private final ActualBindings bindings;

  public AllocateClass(IOrigin origin, Attributes attributes, Type path, ActualBindings bindings) {
    super(origin, attributes);
    this.path = path;
    this.bindings = bindings;
  }

  public Type getPath() {
    return this.path;
  }

  public ActualBindings getBindings() {
    return this.bindings;
  }
}
