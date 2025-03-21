package com.aws.jverify.generated;

// Generated Attributes.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Attributes extends NodeWithOrigin {
  private final String name;

  private final List<Expression> args;

  @Nullable
  private final Attributes prev;

  public Attributes(IOrigin origin, String name, List<Expression> args, Attributes prev) {
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
