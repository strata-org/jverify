package com.aws.jverify.verifier.compiler;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.util.Objects;
import java.util.Stack;

public class Reporter {
    public final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    public JCDiagnostic.Factory diagnosticFactory;
    public JCTree.JCCompilationUnit compilationUnit;
    public final Stack<IOrigin> contextOrigins = new Stack<>();

    public static Reporter instance(Context context) {
        Reporter instance = context.get(Reporter.class);
        if (instance == null) {
            instance = new Reporter(context);
        }
        return instance;
    }

    public Reporter(Context context) {
        context.put(Reporter.class, this);
        this.diagnosticFactory = JCDiagnostic.Factory.instance(context);
    }

    public void reportError(IOrigin origin, String key, Object... args) {
        reportError(positionFromOrigin(origin), key, args);
    }

    public JCDiagnostic.DiagnosticPosition positionFromOrigin(IOrigin origin) {
        return new DiagnosticPositionFromOrigin(Reporter.originToRange(origin), compilationUnit.lineMap);
    }
    
    public void reportError(JCTree tree, String key, Object... args) {
        reportError(positionFromNode(tree, compilationUnit), key, args);
    }

    public void reportError(JCDiagnostic.DiagnosticPosition position, String key, Object... args) {
        reportDiagnostic(position, JCDiagnostic.DiagnosticType.ERROR, key, args);
    }

    public void reportDiagnostic(IOrigin origin, JCDiagnostic.DiagnosticType type,  String key, Object... args) {
        reportDiagnostic(positionFromOrigin(origin), type, key, args);
    }
    
    public void reportDiagnostic(JCDiagnostic.DiagnosticPosition position, 
                                 JCDiagnostic.DiagnosticType type, 
                                 String key, Object... args) {
        this.diagnostics.report(diagnosticFactory.create(type,
                new DiagnosticSource(compilationUnit.getSourceFile(), null), position, key,
                args));
    }

    private JCDiagnostic.DiagnosticPosition positionFromNode(JCTree node, JCTree.JCCompilationUnit compilationUnit) {
        Objects.requireNonNull(node);
        return new JCDiagnostic.DiagnosticPosition() {
            @Override
            public JCTree getTree() {
                return node;
            }

            @Override
            public int getStartPosition() {
                return node.getStartPosition();
            }

            @Override
            public int getPreferredPosition() {
                return node.getPreferredPosition();
            }

            @Override
            public int getEndPosition(EndPosTable endPosTable) {
                return node.getEndPosition(compilationUnit.endPositions);
            }
        };
    }

    public IOrigin toOrigin(JCTree node) {
        var positionCalculator = new PositionCalculator(compilationUnit);
        var startToken = positionCalculator.toToken(TreeInfo.getStartPos(node));
        if (startToken == null) {
            return contextOrigins.peek();
        }
        int endPos = positionCalculator.getEndPos(node);
        var endToken = endPos == Position.NOPOS
                ? positionCalculator.toToken(TreeInfo.getStartPos(node) + 1)
                : positionCalculator.toToken(endPos);
        return new TokenRangeOrigin(startToken, endToken);
    }

    public SourceOrigin declToOrigin(JCTree node, Name name) {
        var entireRange = toOrigin(node);
        return new SourceOrigin(originToRange(entireRange), originToRange(name.getOrigin()));
    }

    public static TokenRange originToRange(IOrigin tokenRangeOrigin) {
        if (tokenRangeOrigin instanceof SourceOrigin sourceOrigin) {
            return new TokenRange(sourceOrigin.getEntireRange().getStartToken(), sourceOrigin.getEntireRange().getEndToken());
        } else if (tokenRangeOrigin instanceof TokenRangeOrigin trOrigin) {
            return new TokenRange(trOrigin.getStartToken(), trOrigin.getEndToken());
        } else {
            throw new RuntimeException(tokenRangeOrigin.getClass().getName());
        }
    }
}
