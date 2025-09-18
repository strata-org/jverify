package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

public class JVerifyUtils {

    private final JavacElements elements;
    private final TreeMaker maker;
    private final Names names;
    private final Symtab symtab;
    private final Types types;

    public static JVerifyUtils instance(Context context) {
        JVerifyUtils instance = context.get(JVerifyUtils.class);
        if (instance == null) {
            instance = new JVerifyUtils(context);
        }
        return instance;
    }

    private JVerifyUtils(Context context) {
        context.put(JVerifyUtils.class, this);
        elements = JavacElements.instance(context);
        maker = TreeMaker.instance(context);
        names = Names.instance(context);
        symtab = Symtab.instance(context);
        types = Types.instance(context);
    }

    public JCTree.JCAnnotation getVerifyFalseAnnotation() {
        var verifySymbol = getVerifyClassSymbol();
        JCTree.JCIdent value = maker.Ident(names.fromString("value"));
        value.sym = symtab.booleanType.tsym;
        return maker.Annotation(maker.Ident(verifySymbol), List.of(
                maker.Assign(value, maker.Literal(false))));
    }

    public Symbol.ClassSymbol getVerifyClassSymbol() {
        return elements.getTypeElement(Verify.class.getCanonicalName());
    }

    public void addVerifyFalseToMethodSymbol(Symbol.MethodSymbol contractMethod, Symbol.MethodSymbol contractee) {
        ListBuffer<Attribute.Compound> newAnnotations = new ListBuffer<>();
        newAnnotations.addAll(contractMethod.getAnnotationMirrors());
        newAnnotations.add(getVerifyAnnotation());
        contractee.resetAnnotations();
        contractee.setDeclarationAttributes(newAnnotations.toList());
    }
    
    public Attribute.Compound getVerifyAnnotation() {
        ListBuffer<Pair<Symbol.MethodSymbol, Attribute>> values = new ListBuffer<>();

        Symbol.MethodSymbol valueMethod = null;
        for (Symbol member : getVerifyClassSymbol().members().getSymbols()) {
            if (member instanceof Symbol.MethodSymbol && member.name.toString().equals("value")) {
                valueMethod = (Symbol.MethodSymbol) member;
                break;
            }
        }

        if (valueMethod != null) {
            Attribute falseAttr = new Attribute.Constant(valueMethod.type, false);
            values.add(new Pair<>(valueMethod, falseAttr));
        }

        return new Attribute.Compound(
                getVerifyClassSymbol().type,
                values.toList()
        );
    }

    public JCTree.JCStatement contractThrow() {
        var contractSymbol = elements.getTypeElement(ContractException.class.getCanonicalName());
        return maker.Throw(maker.Ident(contractSymbol));
    }

    public boolean isPure(Symbol.MethodSymbol rider) {
        if (rider.getAnnotation(Pure.class) != null) {
            return true;
        }

        var riderClass = rider.enclClass();
        for (Type superType : types.closure(riderClass.type)) {
            if (superType.tsym != riderClass) {
                for (Symbol member : superType.tsym.members().getSymbolsByName(rider.name)) {
                    if (member instanceof Symbol.MethodSymbol ridee &&
                            elements.overrides(rider, ridee, ridee.enclClass())) {
                        return isPure(ridee);
                    }
                }
            }
        }
        
        return false;
    }
}
