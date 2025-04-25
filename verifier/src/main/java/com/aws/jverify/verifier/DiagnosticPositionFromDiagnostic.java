package com.aws.jverify.verifier;

import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

record DiagnosticPositionFromDiagnostic(
        Diagnostic<? extends JavaFileObject> diagnostic) implements JCDiagnostic.DiagnosticPosition {

    @Override
    public JCTree getTree() {
        return null;
    }

    @Override
    public int getStartPosition() {
        return (int) diagnostic.getStartPosition();
    }

    @Override
    public int getPreferredPosition() {
        return (int) diagnostic.getPosition();
    }

    @Override
    public int getEndPosition(EndPosTable endPosTable) {
        return (int) diagnostic.getEndPosition();
    }
}
