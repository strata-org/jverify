package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.JVerify;
import com.aws.jverify.Nullable;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import java.util.Set;

/**
 * Must run before lower
 */
public class IsAbstractCompiler extends TreeScanner {
    private final TreeMaker treeMaker;
    private final Reporter reporter;
    private final JVerifyIndex index;
    private final JVerifyUtils utils;
    private final Symbol.MethodSymbol isAbstract;
    private final Names names;
    private final Types types;
    private final Symtab symtab;
    private final MethodOrLoopContractCompiler contractCompiler;
    private Name preconditionMethodPrefix;

    public IsAbstractCompiler(Context context) {
        symtab = Symtab.instance(context);
        names = Names.instance(context);
        treeMaker = TreeMaker.instance(context);
        reporter = Reporter.instance(context);
        types = Types.instance(context);
        utils = JVerifyUtils.instance(context);
        index = JVerifyIndex.instance(context);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        JavacElements  elements = JavacElements.instance(context);
        isAbstract = (Symbol.MethodSymbol) utils.findSymbol(JVerify.class, "isAbstract");
        preconditionMethodPrefix = names.fromString("thePreconditionOf$");
    }
    
    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            scan(env);
        }
        return envs;
    }

    public boolean isAbstract(JCTree.JCExpression expression) {
        if (!(expression instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        Symbol calledMethod = TreeInfo.symbol(invocation.getMethodSelect());
        return isAbstract == calledMethod || calledMethod.name.startsWith(preconditionMethodPrefix);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        super.visitMethodDef(tree);
        var contract = contractCompiler.getContract(tree.body);
        JCTree.JCMethodDecl baseMethod = getBaseMethod(tree);
        var isAbstract = isAbstract(contract.precondition().get());
        boolean isBaseAbstract = false;
        if (baseMethod != null) {
            var baseContract = contractCompiler.getContract(baseMethod.body);
            isBaseAbstract = isAbstract(baseContract.precondition().get());
        }

        if (isAbstract && tree.sym.isStatic()) {
            reporter.reportError(contract.precondition().get(), "staticAbstractContract");
            return;
        }

        boolean implementingAbstractClause = !isAbstract && isBaseAbstract;
        if (implementingAbstractClause) {
            var preconditionFunction = addPreconditionFunctionFor(tree, contract.precondition().get());
            updatePrecondition(tree, contract, preconditionFunction);
        }

        if (isAbstract) {
            var preconditionFunction = addPreconditionFunctionFor(tree, null);
            updatePrecondition(tree, contract, preconditionFunction);
        }
    }

    private JCTree.JCMethodDecl getBaseMethod(JCTree.JCMethodDecl tree) {
        var baseSymbol = utils.getBase(tree.sym);
        if (baseSymbol == null) {
            return null;
        }
        return (JCTree.JCMethodDecl) index.getTree(baseSymbol);
    }

    private void updatePrecondition(JCTree.JCMethodDecl tree, MethodOrLoopContract contract, 
                                    Symbol.MethodSymbol preconditionFunction) {
        var thisSymbol = utils.thisSymbol(tree.sym);

        JCTree.JCMethodInvocation application = treeMaker.Apply(null /* TODO */,
                treeMaker.Select(treeMaker.Ident(thisSymbol), preconditionFunction),
                tree.params.map(d -> treeMaker.Ident(d.sym)).stream().collect(List.collector()));
        application.type = symtab.booleanType;
        contract.precondition().set(application);
    }

    private Symbol.MethodSymbol addPreconditionFunctionFor(JCTree.JCMethodDecl tree, 
                                                           JCTree.@Nullable JCExpression precondition) {
        var clazzSymbol = (Symbol.ClassSymbol)tree.sym.getEnclosingElement();
        var clazz = (JCTree.JCClassDecl)index.getTree(clazzSymbol);
        Name preconditionName = preconditionMethodPrefix.append(tree.name);
        Type methodType = tree.sym.type;

        var preconditionMethodSymbol = new Symbol.MethodSymbol(Flags.PUBLIC, preconditionName, new Type.MethodType(
                methodType.getParameterTypes(), symtab.booleanType, List.nil(), tree.type.tsym), clazzSymbol);
        preconditionMethodSymbol.params = tree.sym.params.map(vs -> new Symbol.VarSymbol(
                vs.flags(), vs.name, vs.type, preconditionMethodSymbol)
        ).stream().collect(List.collector());
        
        JCTree.JCBlock body = treeMaker.Block(0, contractCompiler.getOuterBlockStatements(
                List.nil(), precondition == null ? List.nil() : 
                        java.util.List.of(treeMaker.Return(precondition))));
        
        Symbol.ClassSymbol pureSymbol = utils.getPureClassSymbol();
        JCTree.JCMethodDecl preconditionMethod = treeMaker.MethodDef(preconditionMethodSymbol, body);
        preconditionMethod.mods.annotations = preconditionMethod.mods.annotations.append(
                treeMaker.Annotation(treeMaker.Ident(pureSymbol), List.nil()));

        ListBuffer<Attribute.Compound> newAnnotations = new ListBuffer<>();
        newAnnotations.add(new Attribute.Compound(pureSymbol.type, List.nil()));
        preconditionMethodSymbol.setDeclarationAttributes(newAnnotations.toList());
        
        clazz.defs = clazz.defs.append(preconditionMethod);
        clazz.sym.members().enter(preconditionMethodSymbol);
        return preconditionMethodSymbol;
    }
}
