package com.aws.jverify.generated;

// Generated MultiSelectExpr.java:
// Generated from C# class
import java.util.List;

public class MultiSelectExpr extends Expression {
  private final Expression array;

  private final List<Expression> indices;

  public MultiSelectExpr(IOrigin origin, Expression array, List<Expression> indices) {
    super(origin);
    this.array = array;
    this.indices = indices;
  }

  public Expression getArray() {
    return this.array;
  }

  public List<Expression> getIndices() {
    return this.indices;
  }
}
