package com.aws.jverify.verifier;

import com.aws.jverify.verifier.compiler.position.TokenRange;
import com.sun.tools.javac.tree.JCTree;

public class JavaMethodVerificationStatus {
    private JCTree.JCMethodDecl methodTree;
    private TokenRange position;
    private VerificationStatus verificationStatus;

    public enum VerificationStatus {
        Skipped,
        Verified,
        Failed
    }

    public JavaMethodVerificationStatus(JCTree.JCMethodDecl methodTree, TokenRange position, VerificationStatus status) {
        this.methodTree = methodTree;
        this.position = position;
        this.verificationStatus = status;
    }

    public JCTree.JCMethodDecl getMethodTree() {
        return methodTree;
    }

    public TokenRange getPosition() {
        return position;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus status) {
        this.verificationStatus = status;
    }

    public void setPosition(TokenRange position) {
        this.position = position;
    }

    public void setMethodTree(JCTree.JCMethodDecl methodTree) {
        this.methodTree = methodTree;
    }
}
