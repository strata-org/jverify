package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

class Frame {
    final JCTree tree;
    List<Symbol> locals;

    public Frame(JCTree tree) {
        this.tree = tree;
    }

    void addLocal(Symbol sym) {
        if (locals == null) {
            locals = List.nil();
        }
        locals = locals.prepend(sym);
    }
}
