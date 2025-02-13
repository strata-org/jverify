package org.example.generated;

// Generated DisplayExpression.java:
// Generated from C# class
import java.util.List;

public abstract class DisplayExpression extends Expression {
  private final List<Expression> elements;

  public DisplayExpression(SourceOrigin origin, List<Expression> elements) {
    super(origin);
    this.elements = elements;
  }

  public List<Expression> getElements() {
    return this.elements;
  }
}
