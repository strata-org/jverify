package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.generated.*;
import com.sun.tools.javac.tree.JCTree;

import java.util.ArrayList;
import java.util.List;

public class MethodOrLoopDafnyContract {
    public JCTree treeOrigin;
    
    /**
     * indicates the code is deterministic and does not modify the heap nor do IO
     */
    public boolean isPure;
    public Expression pureBody;
    public List<AttributedExpression> preconditions;
    public List<AttributedExpression> postconditions;
    public List<AttributedExpression> loopInvariants;
    public List<Expression> decreases;
    public List<FrameExpression> reads;
    public List<FrameExpression> modifies;

    public MethodOrLoopDafnyContract(JCTree treeOrigin, boolean isPure) {
        this.treeOrigin = treeOrigin;
        this.isPure = isPure;

        preconditions = new ArrayList<>();
        postconditions = new ArrayList<>();
        loopInvariants = new ArrayList<>();
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
