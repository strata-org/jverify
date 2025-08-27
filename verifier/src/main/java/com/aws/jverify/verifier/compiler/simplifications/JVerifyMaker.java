package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Verify;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

public class JVerifyMaker {

    private final JavacElements elements;
    private final TreeMaker maker;
    private final Names names;
    private final Symtab symtab;

    public static JVerifyMaker instance(Context context) {
        JVerifyMaker instance = context.get(JVerifyMaker.class);
        if (instance == null) {
            instance = new JVerifyMaker(context);
        }
        return instance;
    }

    private JVerifyMaker(Context context) {
        context.put(JVerifyMaker.class, this);
        elements = JavacElements.instance(context);
        maker = TreeMaker.instance(context);
        names = Names.instance(context);
        symtab = Symtab.instance(context);
    }

    public JCTree.JCAnnotation getVerifyFalseAnnotation() {
        var verifySymbol = elements.getTypeElement(Verify.class.getCanonicalName());
        JCTree.JCIdent value = maker.Ident(names.fromString("value"));
        value.sym = symtab.booleanType.tsym;
        return maker.Annotation(maker.Ident(verifySymbol), List.of(
                maker.Assign(value, maker.Literal(false))));
    }
}
