package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

/**
 * Issue is that there is a lot to update, syntax and symbols
 * 
 * Maybe we can keep the translation in the Java to Dafny phase, so we only need to think about syntax
 * It would be ideal if we can separate the nullable code though.
 * Maybe we can stack Java -> Dafny translators
 */
public class NullableCompiler extends TreeTranslator {
    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        super.visitSelect(tree);
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation tree) {
        super.visitApply(tree);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        super.visitVarDef(tree);
    }

    @Override
    public void visitBinary(JCTree.JCBinary tree) {
        super.visitBinary(tree);
    }
}
