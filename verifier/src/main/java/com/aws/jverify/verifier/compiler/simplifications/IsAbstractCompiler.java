package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.JVerify;
import com.aws.jverify.Nullable;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.Set;

public class IsAbstractCompiler extends TreeTranslator {
    private final TreeMaker treeMaker;
    private final Reporter reporter;
    private final JVerifyIndex index;
    private final Symbol.MethodSymbol isAbstract;
    private final Names names;
    private final Symtab symtab;
    private final MethodOrLoopContractCompiler contractCompiler;

    public IsAbstractCompiler(Context context) {
        symtab = Symtab.instance(context);
        names = Names.instance(context);
        treeMaker = TreeMaker.instance(context);
        reporter = Reporter.instance(context);
        index = JVerifyIndex.instance(context);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        JavacElements  elements = JavacElements.instance(context);
        var jverify = elements.getTypeElement(JVerify.class.getCanonicalName());
        isAbstract = (Symbol.MethodSymbol)jverify.members().findFirst(names.fromString("isAbstract"));
    }
    
    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }

    public boolean isAbstract(JCTree.JCExpression expression) {
        if (!(expression instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        return isAbstract == TreeInfo.symbol(invocation);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        super.visitMethodDef(tree);
        var contract = contractCompiler.getContract(tree);
        JCTree.JCMethodDecl baseMethod = getBaseMethod(tree);
        var isAbstract = isAbstract(contract.precondition().get());
        boolean isBaseAbstract = false;
        if (baseMethod != null) {
            var baseContract = contractCompiler.getContract(baseMethod);
            isBaseAbstract = isAbstract(baseContract.precondition().get());
        }

        if (isAbstract && tree.sym.isStatic()) {
            reporter.reportError(contract.precondition().get(), "staticAbstractContract");
            return;
        }
        
        if (!isAbstract && isBaseAbstract) {
            var preconditionFunction = addPreconditionFunctionFor(tree, contract.precondition().get());
            updatePrecondition(tree, contract, preconditionFunction);
        }
        
        if (isAbstract && !isBaseAbstract) {
            var preconditionFunction = addPreconditionFunctionFor(tree, null);
            updatePrecondition(tree, contract, preconditionFunction);
        }
    }

    private JCTree.JCMethodDecl getBaseMethod(JCTree.JCMethodDecl tree) {
        throw new RuntimeException("not implemented");
    }

    private void updatePrecondition(JCTree.JCMethodDecl tree, MethodOrLoopContract contract, 
                                    Symbol.MethodSymbol preconditionFunction) {
        Symbol thisSymbol = null;
        contract.precondition().set(
            treeMaker.Apply(null /* TODO */, 
            treeMaker.Select(treeMaker.Ident(thisSymbol), preconditionFunction),
            tree.params.map(d -> treeMaker.Ident(d.sym)).stream().collect(List.collector())));
    }

    private Symbol.MethodSymbol addPreconditionFunctionFor(JCTree.JCMethodDecl tree, 
                                                           JCTree.@Nullable JCExpression precondition) {
        var clazzSymbol = (Symbol.ClassSymbol)tree.sym.getEnclosingElement();
        var clazz = (JCTree.JCClassDecl)index.getTree(clazzSymbol);
        Name preconditionName = names.fromString("preconditionOf$").append(tree.name);
        Type methodType = tree.sym.type;

        var methodSymbol = new Symbol.MethodSymbol(0, preconditionName, new Type.MethodType(
                methodType.getParameterTypes(), symtab.booleanType, List.nil(), tree.type.tsym), clazzSymbol);
        
        JCTree.JCBlock body = null;
        if (precondition != null) {
            body = treeMaker.Block(0, List.of(treeMaker.Exec(precondition)));
        }
        clazz.defs = clazz.defs.append(treeMaker.MethodDef(methodSymbol, body));
        clazzSymbol.members().enter(methodSymbol);
        return methodSymbol;
    }
}
