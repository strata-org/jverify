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
    Name returnName;
    List<AttributedExpression> invariants;
    List<Expression> decreases;
    List<FrameExpression> reads;
    List<FrameExpression> modifies;

    MethodOrLoopContract(JCTree treeOrigin, boolean isPure) {
        this.treeOrigin = treeOrigin;
        this.isPure = isPure;
        
        preconditions = new ArrayList<>();
        postconditions = new ArrayList<>();
        returnName = null;
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

    /**
     * Returns a {@link Formal} representing the {@link #returnName} if present,
     * or {@code null} if absent.
     * Mainly used for the named result of a constructed {@link Function}
     * or one of the out-parameters of a constructed {@link Method}.
     */
    public @Nullable Formal makeReturnFormal(Type syntacticType) {
        return returnName == null ? null : new Formal(
                returnName.getOrigin(), returnName, syntacticType,
                false, false,
                null, null,
                false, false, false,
                null
        );
    }
}
