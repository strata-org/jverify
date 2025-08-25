package com.aws.jverify.generated;

// Generated AllocateArray.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AllocateArray extends TypeRhs {
  @Nullable
  private final Type explicitType;

  private final List<Expression> arrayDimensions;

  @Nullable
  private final Expression elementInit;

  public AllocateArray(IOrigin origin, Attributes attributes, Type explicitType,
      List<Expression> arrayDimensions, Expression elementInit) {
    super(origin, attributes);
    this.explicitType = explicitType;
    this.arrayDimensions = arrayDimensions;
    this.elementInit = elementInit;
  }

  public Type getExplicitType() {
    return this.explicitType;
  }

  public List<Expression> getArrayDimensions() {
    return this.arrayDimensions;
  }

  public Expression getElementInit() {
    return this.elementInit;
  }
}
