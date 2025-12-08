package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.JVerify;
import com.aws.jverify.Nullable;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;

/**
 * Must run before lower
 * <p>
 * Handles 'isAbstract()' calls, which can be used as the argument of a contract clause
 * <p>
 * If an 'isAbstract()' call is encountered, it is replaced by a call to a 'thePreconditionOf$' method, 
 * specific to the current method and type of clause. 
 * If this thePreconditionOf$ method does not exist yet in the inheritance hierarchy, it is created.
 * <p>
 * If a contract clause does not have an 'isAbstract()' body, but the base method does,
 * then an implementation of the related `thePreconditionOf$` is generated.
 * <p>
 * Example:
 * <p>
 * abstract class Bottom {
 *     int foo(int x) {
 *         precondition(isAbstract());
 *         throw new ContractException();
 *     }
 * }
 * abstract class Mid extends Bottom {
 *     int foo(int x) {
 *         precondition(isAbstract());
 *         postcondition((int r) -> r > 0);
 *     }
 * }
 * class Top extends Mid {
 *     int foo(int x) {
 *         precondition(x > 10);
 *         postcondition((int r) -> r > 0);
 *     }
 * }
 * <p>
 * Translates to
 * <p></p>
 * abstract class Bottom {
 *     boolean thePreconditionOf$foo(int x);
 *     int foo(int x) {
 *         precondition(thePreconditionOf$foo(x));
 *         throw new ContractException();
 *     }
 * }
 * class Mid extends Bottom {
 *     int foo(int x) {
 *         precondition(thePreconditionOf$foo(x));
 *         postcondition((int r) -> r > 0);
 *     }
 * }
 * class Top extends Mid {
 *     boolean thePreconditionOf$foo(int x) {
 *         return x > 10;
 *     }
 *     int foo(int x) {
 *         precondition(thePreconditionOf$foo(x));
 *         postcondition((int r) -> r > 0);
 *     }
 * }
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
    private final Context context;
    private Name preconditionMethodPrefix;

    public IsAbstractCompiler(Context context) {
        this.context = context;
        symtab = Symtab.instance(context);
        names = Names.instance(context);
        treeMaker = TreeMaker.instance(context);
        reporter = Reporter.instance(context);
        types = Types.instance(context);
        utils = JVerifyUtils.instance(context);
        index = JVerifyIndex.instance(context);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        isAbstract = (Symbol.MethodSymbol) utils.findSymbol(JVerify.class, "isAbstract");
        preconditionMethodPrefix = names.fromString("thePreconditionOf$");
    }
    
    public java.util.List<JCTree.JCCompilationUnit> transform(java.util.List<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            reporter.compilationUnit = env;
            scan(env);
        }
        return envs;
    }

    public boolean isAbstract(java.util.List<Property<JCTree.JCExpression>> expressions) {
        if (expressions.size() != 1) {
            return false;
        }
        var expression = expressions.getFirst().get();
        if (!(expression instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        Symbol calledMethod = TreeInfo.symbol(invocation.getMethodSelect());
        boolean isAbstractCallWasAlreadyReplaced = calledMethod.name.startsWith(preconditionMethodPrefix);
        return isAbstract == calledMethod || isAbstractCallWasAlreadyReplaced;
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        super.visitMethodDef(tree);
        if (tree.body == null) {
            return;
        }
        var contract = contractCompiler.getContract(tree.body);
        JCTree.JCMethodDecl baseMethod = getBaseMethod(tree);
        var isAbstract = isAbstract(contract.preconditions());
        boolean isBaseAbstract = false;
        if (baseMethod != null && baseMethod.body != null) {
            var baseContract = contractCompiler.getContract(baseMethod.body);
            isBaseAbstract = isAbstract(baseContract.preconditions());
        }

        if (isAbstract && tree.sym.isStatic()) {
            reporter.reportError(contract.preconditions().getFirst().get(), "staticAbstractContract");
            return;
        }

        boolean implementingAbstractClause = !isAbstract && isBaseAbstract;
        if (implementingAbstractClause) {
            var precondition = MethodOrLoopContract.combineClauses(context, contract.preconditions());
            var preconditionFunction = addPreconditionFunctionFor(tree, precondition);
            if (!contract.preconditions().isEmpty()) {
                updatePrecondition(tree, contract, preconditionFunction);
            }
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

        JCTree.JCMethodInvocation application = treeMaker.Apply(List.nil(),
                treeMaker.Select(treeMaker.Ident(thisSymbol), preconditionFunction),
                tree.params.map(d -> treeMaker.Ident(d.sym)).stream().collect(List.collector()));
        application.type = symtab.booleanType;
        contract.preconditions().getFirst().set(application);
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
