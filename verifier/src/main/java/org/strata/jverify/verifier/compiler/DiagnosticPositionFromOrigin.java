package org.strata.jverify.verifier.compiler;

import org.strata.jverify.verifier.compiler.position.TokenRange;
import com.sun.source.tree.LineMap;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;

public record DiagnosticPositionFromOrigin(
        TokenRange range, LineMap lineMap) implements JCDiagnostic.DiagnosticPosition {

    @Override
    public JCTree getTree() {
        return null;
    }

    @Override
    public int getStartPosition() {
        return (int)lineMap.getPosition(range.startToken().line(), range.startToken().col() - 1);
    }

    @Override
    public int getPreferredPosition() {
        return getStartPosition();
    }

    @Override
    public int getEndPosition(EndPosTable endPosTable) {
        return (int)lineMap.getPosition(range.endToken().line(), range.endToken().col() - 1);
    }
}
