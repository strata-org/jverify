package com.aws.jverify.generated;

// Generated ConcreteAssignStatement.java:
// Generated from C# class
import java.util.List;

public abstract class ConcreteAssignStatement extends Statement {
  private final List<Expression> lhss;

  public ConcreteAssignStatement(SourceOrigin origin, Attributes attributes,
      List<Expression> lhss) {
    super(origin, attributes);
    this.lhss = lhss;
  }

  public List<Expression> getLhss() {
    return this.lhss;
  }
}
