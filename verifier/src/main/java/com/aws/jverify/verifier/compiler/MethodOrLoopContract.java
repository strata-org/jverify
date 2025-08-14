package com.aws.jverify.verifier.compiler;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;

import java.util.ArrayList;
import java.util.List;

public class MethodOrLoopContract {
    JCTree treeOrigin;
    
    /**
     * indicates the code is deterministic and does not modify the heap nor do IO
     */
    public boolean isPure;
    Expression pureBody;
    List<AttributedExpression> preconditions;
    List<AttributedExpression> postconditions;
    List<AttributedExpression> invariants;
    List<Expression> decreases;
    List<FrameExpression> reads;
    List<FrameExpression> modifies;

    public MethodOrLoopContract(JCTree treeOrigin, boolean isPure) {
        this.treeOrigin = treeOrigin;
        this.isPure = isPure;

        preconditions = new ArrayList<>();
        postconditions = new ArrayList<>();
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
