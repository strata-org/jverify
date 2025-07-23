package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.tree.JCTree.Tag.IMPORT;
import static com.sun.tools.javac.tree.JCTree.Tag.MODULEDEF;
import static com.sun.tools.javac.tree.JCTree.Tag.MODULEIMPORT;
import static com.sun.tools.javac.tree.JCTree.Tag.PACKAGEDEF;

/**
 * A workaround for the fact that we can't create new JCCompilationUnit instances,
 * but the LOWER phase
 * @param unit
 * @param newDefs
 */
public record JVerifyCompilationUnit(JCTree.JCCompilationUnit unit, List<JCTree> newDefs) {

    public static JVerifyCompilationUnit of(JCTree.JCCompilationUnit unit) {
        return new JVerifyCompilationUnit(unit, unit.defs);
    }
}
