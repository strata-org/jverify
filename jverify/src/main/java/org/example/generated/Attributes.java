package org.example.generated;

// Generated Attributes.java:
// Generated from C# class
import java.util.List;

public class Attributes extends NodeWithComputedRange {
  private final String name;

  private final List<Expression> args;

  private final Attributes prev;

  public Attributes(SourceOrigin origin, String name, List<Expression> args, Attributes prev) {
    super(origin);
    this.name = name;
    this.args = args;
    this.prev = prev;
  }

  public String getName() {
    return this.name;
  }

  public List<Expression> getArgs() {
    return this.args;
  }

  public Attributes getPrev() {
    return this.prev;
  }
}
