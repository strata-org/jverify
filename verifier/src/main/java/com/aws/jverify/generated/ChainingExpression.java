package com.aws.jverify.generated;

// Generated ChainingExpression.java:
// Generated from C# class
import java.util.List;

public class ChainingExpression extends ConcreteSyntaxExpression {
  private final List<Expression> operands;

  private final List<BinaryExprOpcode> operators;

  private final List<IOrigin> operatorLocs;

  private final List<Expression> prefixLimits;

  public ChainingExpression(IOrigin origin, List<Expression> operands,
      List<BinaryExprOpcode> operators, List<IOrigin> operatorLocs, List<Expression> prefixLimits) {
    super(origin);
    this.operands = operands;
    this.operators = operators;
    this.operatorLocs = operatorLocs;
    this.prefixLimits = prefixLimits;
  }

  public List<Expression> getOperands() {
    return this.operands;
  }

  public List<BinaryExprOpcode> getOperators() {
    return this.operators;
  }

  public List<IOrigin> getOperatorLocs() {
    return this.operatorLocs;
  }

  public List<Expression> getPrefixLimits() {
    return this.prefixLimits;
  }
}
