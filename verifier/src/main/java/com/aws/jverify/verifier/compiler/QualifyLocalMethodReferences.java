package com.aws.jverify.verifier.compiler;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

/**
 * Whenever 'this.' or '<ContaingType>.' is implicit, adds it.
 * This is also done by the lower phase, but we repeat the logic here so it can be used before the lower phase.
 */
public class QualifyLocalMethodReferences extends TreeTranslator {
    private final TreeMaker make;
    private final Names names;
    

    public QualifyLocalMethodReferences(Context context) {
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }
    
    @Override
    public void visitIdent(JCTree.JCIdent tree) {
        if (tree.sym instanceof Symbol.MethodSymbol methodSymbol) {
            JCTree.JCIdent thisIdent;
            if (methodSymbol.isStatic()) {
                Name name = methodSymbol.owner.name;
                thisIdent = make.Ident(name);
                thisIdent.sym = methodSymbol.owner;
                thisIdent.type = thisIdent.sym.type;
            } else {
                Name name = names._this;
                thisIdent = make.Ident(name);
                thisIdent.sym = new Symbol.VarSymbol(0, name, methodSymbol.owner.type, methodSymbol.owner);
                thisIdent.type = thisIdent.sym.type;
            }
            
            result = make.Select(thisIdent, methodSymbol);
        } else {
            super.visitIdent(tree);
        }
    }
}
