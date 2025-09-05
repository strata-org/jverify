package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.util.ArrayList;
import java.util.Set;

class JCMethodDeclWithContract extends JCTree.JCMethodDecl {
    ContractContainer contractContainer;

    public JCMethodDeclWithContract(JCMethodDecl original, 
                                    ContractContainer contractContainer) {

        super(original.mods, original.name, original.restype, original.typarams,
                original.recvparam, original.params, original.thrown, original.body,
                original.defaultValue,
                original.sym);
        this.contractContainer = contractContainer;
    }
}

class ContractContainer {
    public java.util.List<JCTree.JCExpression> preconditions;
    public java.util.List<JCTree.JCExpression> postconditions;
    public java.util.List<JCTree.JCExpression> invariants;
    public java.util.List<JCTree.JCExpression> decreases;
    public java.util.List<JCTree.JCExpression> reads;
    public java.util.List<JCTree.JCExpression> modifies;
}


/*
Extracts contracts from constructor, method or loop bodies
Does not support lambdas.
TODO: could be slightly simplified by compiling DoWhile loops first
 */
public class MethodOrLoopContractCompiler extends TreeTranslator {
    private final TreeMaker maker;
    private final Reporter reporter;
    private final JVerifyUtils jverifyUtils;

    private MethodOrLoopContractCompiler(Context context) {
        context.put(MethodOrLoopContractCompiler.class, this);
        this.maker = TreeMaker.instance(context);
        this.reporter = Reporter.instance(context);
        this.jverifyUtils = JVerifyUtils.instance(context);
    }

    public static MethodOrLoopContractCompiler instance(Context context) {
        MethodOrLoopContractCompiler instance = context.get(MethodOrLoopContractCompiler.class);
        if (instance == null) {
            instance = new MethodOrLoopContractCompiler(context);
        }
        return instance;
    }
    
    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        reporter.compilationUnit = tree;
        super.visitTopLevel(tree);
    }

    @Override
    public void visitDoLoop(JCTree.JCDoWhileLoop tree) {
        if (tree.body != null) {
            var contractContainer = new ContractContainer();
            tree.body = getNewStatements(tree, getStatements(tree.body), false, contractContainer);
        }
        super.visitDoLoop(tree);
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop tree) {
        if (tree.body != null) {
            var contractContainer = new ContractContainer();
            tree.body = getNewStatements(tree, getStatements(tree.body), false, contractContainer);
        }
        super.visitWhileLoop(tree);
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop tree) {
        if (tree.body != null) {
            var contractContainer = new ContractContainer();
            tree.body = getNewStatements(tree, getStatements(tree.body), false, contractContainer);
        }
        super.visitForLoop(tree);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        var isPure = jverifyUtils.isPure(tree.sym);
        if (tree.body != null) {
            var allowFooter = JavaToDafnyCompiler.isConstructor(tree.sym);
            var contractContainer = new ContractContainer();
            tree.body = getNewStatements(tree, tree.body.getStatements(), allowFooter, contractContainer);
            result = new JCMethodDeclWithContract(tree, contractContainer);
        }
        super.visitMethodDef(tree);
    }
    
    List<JCTree.JCStatement> getStatements(JCTree.JCStatement statement) {
        return statement instanceof JCTree.JCBlock block ? block.getStatements() : List.of(statement);
    }

    private JCTree.JCBlock getNewStatements(JCTree tree, List<JCTree.JCStatement> statements, 
                                                      boolean allowFooter,
                                                      ContractContainer contract) {
        var superOrThis = getSuperOrThis(statements);

        if (superOrThis != null) {
            statements = statements.tail;
        }

        java.util.List<JCTree.JCStatement> remainingStatements = new ArrayList<>(statements);
        remainingStatements = addHeaderContracts(remainingStatements, contract);

        if (allowFooter) {
            remainingStatements = addFooterContracts(remainingStatements, contract);
        }

        maker.pos = tree.pos;

        if (superOrThis != null) {
            remainingStatements = new ArrayList<>(remainingStatements);
            remainingStatements.addFirst(superOrThis);
        }
        return maker.Block(0, List.from(remainingStatements));
    }

    private java.util.List<JCTree.JCStatement> addFooterContracts(java.util.List<JCTree.JCStatement> remainingStatements, 
                                                                  ContractContainer contracts) {
        int i;
        for (i = remainingStatements.size() - 1; i > 0; i--) {
            var current = remainingStatements.get(i);
            boolean foundHeader = handleStatement(current, contracts);
            if (!foundHeader) {
                break;
            }
        }
        int footerContracts = i + 1;
        remainingStatements = remainingStatements.subList(0, footerContracts);
        return remainingStatements;
    }

    private static JCTree.JCStatement getSuperOrThis(List<JCTree.JCStatement> statements) {
        JCTree.JCStatement superOrThis = null;
        var first = statements.isEmpty() ? null : statements.getFirst();
        if ((first instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            var isSuperOrThisCall = invocation.getMethodSelect() instanceof JCTree.JCIdent ident && 
                    (ident.name == ident.name.table.names._super || ident.name == ident.name.table.names._this);
            if (isSuperOrThisCall) {
                superOrThis = first;
            }
        }
        return superOrThis;
    }

    private java.util.List<JCTree.JCStatement> addHeaderContracts(
            java.util.List<JCTree.JCStatement> statements,
            ContractContainer contract) {
        int i = 0;
        for (; i < statements.size(); i++) {
            var statement = statements.get(i);
            boolean isContract = handleStatement(statement, contract);
            if (!isContract) {
                break;
            }
        }
        return statements.subList(i, statements.size());
    }

    private boolean isContractStatement(JCTree.JCStatement statement) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return false;
        }
        
        var methodName = jverifyMethod.getQualifiedName().toString();
        switch (methodName) {
            case "check" -> {
                // not a header method, so stop here
                return false;
            }
            case Common.PRECONDITION, "postcondition", "invariant", "decreases", "reads", "modifies" -> {
                return true;
            }
            default -> {
                reporter.reportError(invocation, "notSupported", methodName);
                return false;
            }
        }
    }

    public JCTree.JCBlock getDefaultBody() {
        JCTree.JCStatement implementation = jverifyUtils.contractThrow();
        return maker.Block(0, List.of(
                maker.Block(0, List.nil()),
                implementation));
    }

    private boolean handleStatement(JCTree.JCStatement statement, 
                                          ContractContainer contract) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return false;
        }
        var methodName = jverifyMethod.getQualifiedName().toString();
        switch (methodName) {
            case "check" -> {
                // not a header method, so stop here
                return false;
            }
            case Common.PRECONDITION -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A precondition call may have only one argument");
                }
                contract.preconditions.add(invocation.getArguments().getFirst());
            }
            case "postcondition" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A postcondition call may have only one argument");
                }
                handlePostcondition(contract, invocation.getArguments().getFirst());
            }
            case "invariant" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("invariant should have a single argument");
                }
                contract.invariants.add(invocation.getArguments().getFirst());
            }
            case "decreases" -> {
                for(var decrease : invocation.getArguments()) {
                    // The LOWER javac phase inserts an explicit NewArray for varargs
                    if (decrease instanceof JCTree.JCNewArray newArray) {
                        contract.decreases.addAll(newArray.getInitializers());
                    } else {
                        contract.decreases.add(decrease);
                    }
                }
            }
            case "reads" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A reads call must have exactly one argument");
                }
                contract.reads.add(invocation.getArguments().getFirst());
            }
            case "modifies" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A modifies call must have exactly one argument");
                }
                contract.modifies.add(invocation.getArguments().getFirst());
            }
            default -> {
                reporter.reportError(invocation, "notSupported", methodName);
                return false;
            }
        }
        return true;
    }
//
//    public static boolean handleStatement(JavaToDafnyCompiler compiler, JCTree.JCStatement statement, MethodOrLoopContract contract) {
//        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
//                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
//            return false;
//        }
//        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
//        if (jverifyMethod == null) {
//            return false;
//        }
//        var methodName = jverifyMethod.getQualifiedName().toString();
//        switch (methodName) {
//            case "check" -> {
//                // not a header method, so stop here
//                return false;
//            }
//            case Common.PRECONDITION -> {
//                if (invocation.args.size() != 1) {
//                    throw new JavaViolationException("A precondition call may have only one argument");
//                }
//                contract.preconditions.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst()), null, null));
//            }
//            case "postcondition" -> {
//                if (invocation.args.size() != 1) {
//                    throw new JavaViolationException("A postcondition call may have only one argument");
//                }
//                handlePostcondition(compiler, contract, invocation.getArguments().getFirst());
//            }
//            case "invariant" -> {
//                if (invocation.args.size() != 1) {
//                    throw new JavaViolationException("invariant should have a single argument");
//                }
//                contract.invariants.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst()), null, null));
//            }
//            case "decreases" -> {
//                for(var decrease : invocation.getArguments()) {
//                    // The LOWER javac phase inserts an explicit NewArray for varargs
//                    if (decrease instanceof JCTree.JCNewArray newArray) {
//                        contract.decreases.addAll(newArray.getInitializers().map(compiler.expressionCompiler::toExpr));
//                    } else {
//                        contract.decreases.add(compiler.expressionCompiler.toExpr(decrease));
//                    }
//                }
//            }
//            case "reads" -> {
//                if (invocation.args.size() != 1) {
//                    throw new JavaViolationException("A reads call must have exactly one argument");
//                }
//                var origExpr = invocation.getArguments().getFirst();
//                var origin = compiler.toOrigin(origExpr);
//                var expr = compiler.expressionCompiler.toExpr(origExpr);
//                contract.reads.add(new FrameExpression(origin, expr, null));
//            }
//            case "modifies" -> {
//                if (invocation.args.size() != 1) {
//                    throw new JavaViolationException("A modifies call must have exactly one argument");
//                }
//                var origExpr = invocation.getArguments().getFirst();
//                var origin = compiler.toOrigin(origExpr);
//                var expr = compiler.expressionCompiler.toExpr(origExpr);
//                contract.modifies.add(new FrameExpression(origin, expr, null));
//            }
//            default -> {
//                compiler.reportError(invocation, "notSupported", methodName);
//                return false;
//            }
//        }
//        return true;
//    }

    private static void handlePostcondition(ContractContainer header, JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCLambda lambda) {
            if (lambda.getParameters().size() != 1) {
                throw new JavaViolationException("A postcondition call lambda must take exactly one argument");
            }
            header.postconditions.add(lambda);
//            var parameter = lambda.params.getFirst();
//            var origin = compiler.toOrigin(lambda);
//            var paramName = parameter.getName().toString();
//            var type = compiler.translateType(parameter.type, compiler.toOrigin(parameter), null);
//
//            var returnVar = new BoundVar(origin, new Name(origin, paramName), type, false);
//            var lhs = new CasePattern<>(origin, paramName, returnVar, null);
//            var rhs = TreeInfo.isConstructor(header.treeOrigin)
//                    ? new ThisExpr(origin)
//                    : new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);
//            var origCondition = compiler.expressionCompiler.toExpr(lambda.getBody());
//            var condition = new LetExpr(origin, java.util.List.of(lhs), java.util.List.of(rhs), origCondition, true, null);
//            header.postconditions.add(new AttributedExpression(condition, null, null));

        } else if (expr instanceof JCTree.JCMemberReference memberReference) {
            header.postconditions.add(memberReference);
//            var origin = compiler.toOrigin(memberReference);
//            var argBindings = java.util.List.of(new ActualBinding(null,
//                    new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null), false));
//            var callee = new ExprDotName(origin,
//                    compiler.expressionCompiler.toExpr(memberReference.expr),
//                    compiler.getName(memberReference, compiler.nameCompiler.getCompiledName(memberReference.sym, origin)), null);
//            var call = new ApplySuffix(origin, callee, null,
//                    new ActualBindings(argBindings), null);
//            header.postconditions.add(new AttributedExpression(call, null, null));
        } else if (expr instanceof JCTree.JCTypeCast typeCast) {
            // Casts like (IntPredicate) are sometimes necessary to disambiguate
            handlePostcondition(header, typeCast.getExpression());
        } else {
            header.postconditions.add(expr);
        }
    }
}
