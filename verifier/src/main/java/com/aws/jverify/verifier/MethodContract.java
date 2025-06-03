package com.aws.jverify.verifier;

import com.aws.jverify.generated.*;

import java.util.ArrayList;
import java.util.List;

public class MethodContract {
    List<AttributedExpression> preconditions;
    List<AttributedExpression> postconditions;
    List<Name> returnNames;
    List<AttributedExpression> invariants;
    List<Expression> decreases;
    List<FrameExpression> reads;
    List<FrameExpression> modifies;

    MethodContract() {
        preconditions = new ArrayList<>();
        postconditions = new ArrayList<>();
        returnNames = new ArrayList<>();
        invariants = new ArrayList<>();
        decreases = new ArrayList<>();
        reads = new ArrayList<>();
        modifies = new ArrayList<>();
    }

    public Specification<FrameExpression> getReads() {
        return new Specification<>(reads, null);
    }

    public Specification<FrameExpression> getModifies() {
        return new Specification<>(modifies, null);
    }

    public Specification<Expression> getDecreases() {
        return new Specification<>(decreases, null);
    }
}
