package com.aws.jverify.verifier;

import com.aws.jverify.generated.TokenRange;
import com.sun.source.tree.LineMap;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;

record DiagnosticPositionFromOrigin(
        TokenRange range, LineMap lineMap) implements JCDiagnostic.DiagnosticPosition {

    @Override
    public JCTree getTree() {
        return null;
    }

    @Override
    public int getStartPosition() {
        return (int) lineMap.getStartPosition(range.getStartToken().getLine() - 1) + range.getStartToken().getCol() - 1;
    }

    @Override
    public int getPreferredPosition() {
        return getStartPosition();
    }

    @Override
    public int getEndPosition(EndPosTable endPosTable) {
        return (int) lineMap.getStartPosition(range.getEndToken().getLine() - 1) + range.getEndToken().getCol() - 1;
    }
}
