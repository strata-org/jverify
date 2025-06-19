package com.aws.jverify.verifier;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MethodOrLoopContract {
    JCTree treeOrigin;
    /**
     * indicates the code is deterministic and does not modify the heap nor do IO
     */
    boolean isPure;
    List<AttributedExpression> preconditions;
    List<AttributedExpression> postconditions;
    List<AttributedExpression> invariants;
    List<Expression> decreases;
    List<FrameExpression> reads;
    List<FrameExpression> modifies;

    MethodOrLoopContract(JCTree treeOrigin, boolean isPure) {
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
