package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.tree.JCTree.Tag.IMPORT;
import static com.sun.tools.javac.tree.JCTree.Tag.MODULEDEF;
import static com.sun.tools.javac.tree.JCTree.Tag.MODULEIMPORT;
import static com.sun.tools.javac.tree.JCTree.Tag.PACKAGEDEF;

public record JVerifyCompilationUnit(JCTree.JCCompilationUnit unit, List<JCTree> newDefs) {

    public List<JCTree> getTypeDecls() {
        List<JCTree> typeDefs;
        for (typeDefs = newDefs; !typeDefs.isEmpty(); typeDefs = typeDefs.tail) {
            if (!typeDefs.head.hasTag(MODULEDEF)
                    && !typeDefs.head.hasTag(PACKAGEDEF)
                    && !typeDefs.head.hasTag(IMPORT)
                    && !typeDefs.head.hasTag(MODULEIMPORT)) {
                break;
            }
        }
        return typeDefs;
    }
}
