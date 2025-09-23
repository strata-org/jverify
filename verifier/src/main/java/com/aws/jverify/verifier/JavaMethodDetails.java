package com.aws.jverify.verifier;

import com.aws.jverify.generated.Token;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

public class JavaMethodDetails {
    private JCTree.JCMethodDecl methodTree;
    private Token startPosition;
    private Token endPosition;
    private boolean verifiable;
    private boolean verified;

    public JavaMethodDetails(JCTree.JCMethodDecl methodTree, Token startPosition, Token endPosition, boolean verifiable, boolean verified) {
        this.methodTree = methodTree;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.verifiable = verifiable;
        this.verified = verified;
    }

    public JCTree.JCMethodDecl getMethodTree() {
        return methodTree;
    }

    public Token getStartPosition() {
        return startPosition;
    }

    public Token getEndPosition() {
        return endPosition;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isVerifiable() {
        return verifiable;
    }

    public void setVerifiable(boolean verifiable) {
        this.verifiable = verifiable;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public void setPosition(Token startPosition, Token endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    public void setMethodSymbol(JCTree.JCMethodDecl methodTree) {
        this.methodTree = methodTree;
    }
}
