package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;

abstract class BasicFreeVarCollector extends TreeScanner {
    LambdaCompiler compiler;

    public BasicFreeVarCollector(LambdaCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * Add all free variables of class c to fvs list
     * unless they are already there.
     */
    abstract void addFreeVars(Symbol.ClassSymbol c);

    /**
     * If tree refers to a variable in owner of local class, add it to
     * free variables list.
     */
    public void visitIdent(JCTree.JCIdent tree) {
        visitSymbol(tree.sym);
    }

    // where
    abstract void visitSymbol(Symbol _sym);

    /**
     * If tree refers to a class instance creation expression
     * add all free variables of the freshly created class.
     */
    public void visitNewClass(JCTree.JCNewClass tree) {
        Symbol.ClassSymbol c = (Symbol.ClassSymbol) tree.constructor.owner;
        addFreeVars(c);
        super.visitNewClass(tree);
    }

    /**
     * If tree refers to a superclass constructor call,
     * add all free variables of the superclass.
     */
    public void visitApply(JCTree.JCMethodInvocation tree) {
        if (TreeInfo.name(tree.meth) == compiler.names._super) {
            addFreeVars((Symbol.ClassSymbol) TreeInfo.symbol(tree.meth).owner);
        }
        super.visitApply(tree);
    }

    @Override
    public void visitYield(JCTree.JCYield tree) {
        scan(tree.value);
    }

}
