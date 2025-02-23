package com.aws.jverify.generated;

// Generated ActualBindings.java:
// Generated from C# class
import java.util.List;

public class ActualBindings extends NodeWithComputedRange {
  private final List<ActualBinding> argumentBindings;

  public ActualBindings(IOrigin origin, List<ActualBinding> argumentBindings) {
    super(origin);
    this.argumentBindings = argumentBindings;
  }

  public List<ActualBinding> getArgumentBindings() {
    return this.argumentBindings;
  }
}
