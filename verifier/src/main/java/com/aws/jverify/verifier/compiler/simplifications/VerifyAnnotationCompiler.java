package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Verify;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.Stack;

public class VerifyAnnotationCompiler {
    JavaToDafnyCompiler compiler;

    public VerifyAnnotationCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
        shouldVerifies.push(compiler.verifierOptions.verifyByDefault()
                ? ShouldVerifyMode.DefaultYes
                : ShouldVerifyMode.DefaultNo);
    }

    public final Stack<ShouldVerifyMode> shouldVerifies = new Stack<>();
    
    public enum ShouldVerifyMode { AlwaysYes, DefaultYes, AlwaysNo, DefaultNo, Inherit }
    
    public boolean processVerifyAnnotationAndPop(Map<String, JCTree.JCAnnotation> annotationsByName) {
        processVerifyAnnotation(annotationsByName);
        boolean shouldVerify = shouldVerify();
        shouldVerifies.pop();
        return shouldVerify;
    }

    private boolean shouldVerify() {
        for (int i = shouldVerifies.size() - 1; i >= 0; i--) {
            var mode = shouldVerifies.get(i);
            if (mode == ShouldVerifyMode.AlwaysYes || mode == ShouldVerifyMode.DefaultYes) {
                return true;
            } else if (mode == ShouldVerifyMode.AlwaysNo || mode == ShouldVerifyMode.DefaultNo) {
                return false;
            }
        }
        throw new RuntimeException("shouldVerify should never be empty");
    }

    public void addShouldVerify(ShouldVerifyMode mode) {
        if (shouldVerifies.peek() == ShouldVerifyMode.AlwaysYes) {
            shouldVerifies.push(ShouldVerifyMode.AlwaysYes);
        } else if (shouldVerifies.peek() == ShouldVerifyMode.AlwaysNo) {
            shouldVerifies.push(ShouldVerifyMode.AlwaysNo);
        } else if (mode == ShouldVerifyMode.Inherit) {
            shouldVerifies.push(shouldVerifies.peek());
        } else {
            shouldVerifies.push(mode);
        }
    }

    public void processVerifyAnnotation(@Nullable Verify verify) {
        if (verify != null) {
            var should = verify.value();
            var includeMembers = verify.overrideChildren();
            addShouldVerify(getVerifyMode(should, includeMembers));
        } else {
            addShouldVerify(ShouldVerifyMode.Inherit);
        }
    }
    
    public void processVerifyAnnotation(Map<String, JCTree.JCAnnotation> annotationsByName) {
        ShouldVerifyMode mode = getShouldVerifyMode(annotationsByName);
        addShouldVerify(mode);
    }
    
    public ShouldVerifyMode getShouldVerifyMode(Map<String, JCTree.JCAnnotation> annotationsByName) {
        ShouldVerifyMode mode;
        var verifyAnnotation = annotationsByName.get(Verify.class.getName());
        if (verifyAnnotation != null) {
            var arguments = JavaToDafnyCompiler.getArguments(verifyAnnotation);
            var shouldArgument = arguments.get("value");
            var should = true;
            if (shouldArgument != null) {
                should = (boolean) JavaToDafnyCompiler.getLiteralValue(shouldArgument);
            }

            var pushDownArgument = arguments.get("overrideChildren");
            var includeMembers = true;
            if (pushDownArgument != null) {
                includeMembers = (boolean) JavaToDafnyCompiler.getLiteralValue(pushDownArgument);
            }
            mode = getVerifyMode(should, includeMembers);
        } else {
            mode = ShouldVerifyMode.Inherit;
        }
        return mode;
    }

    private ShouldVerifyMode getVerifyMode(boolean should, boolean pushDown) {
        if (should) {
            if (pushDown) {
                return ShouldVerifyMode.AlwaysYes;
            } else {
                return ShouldVerifyMode.DefaultYes;
            }
        } else {
            if (pushDown) {
                return ShouldVerifyMode.AlwaysNo;
            } else {
                return ShouldVerifyMode.DefaultNo;
            }
        }
    }
}
