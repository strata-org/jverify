package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionCompiler;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionContext;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Stream;

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

    public static JCTree.@Nullable JCBlock getImplementation(JCTree.JCMethodDecl method) {
        JCTree.JCStatement second = method.body.getStatements().get(1);
        if (second instanceof JCTree.JCBlock block) {
            return block;
        }
        return null;
    }
    
    public static boolean hasImplementation(JCTree.JCMethodDecl method) {
        return method.body != null && method.body.getStatements().get(1) instanceof JCTree.JCBlock;
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
    
    public List<JCTree.JCStatement> extractContract(BaseDafnyGenerator compiler,
                                                    JCTree.JCBlock block,
                                                    MethodOrLoopContract contract) {
        if (block.getStatements().size() != 2) {
            throw new RuntimeException("Method body is not in contract + implementation format");
        }
        var contracts = (JCTree.JCBlock)block.getStatements().get(0);
        var implementation = block.getStatements().get(1);
        var hasImplementation = implementation instanceof JCTree.JCBlock;
        for(var contractStatement : contracts.getStatements()) {
            handleStatement(compiler, contractStatement, contract);
        }

        List<JCTree.JCStatement> implementationStatements = hasImplementation 
                ? ((JCTree.JCBlock) implementation).getStatements() 
                : List.nil();
        if (contract.isPure) {
            if (!implementationStatements.isEmpty()) {
                contract.pureBody = compiler.expressionCompiler.toExprWithFlows(implementationStatements, ExpressionContext.Pure);
            }
            return List.nil();
        }
        return implementationStatements;
    }
    
    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        reporter.compilationUnit = tree;
        super.visitTopLevel(tree);
    }

    @Override
    public void visitDoLoop(JCTree.JCDoWhileLoop tree) {
        if (tree.body != null) {
            maker.pos = tree.body.pos;
            List<JCTree.JCStatement> newStatements = getNewStatements(tree, getStatements(tree.body), false);
            tree.body = maker.Block(0, newStatements);
        }
        super.visitDoLoop(tree);
    }

    @Override
    public void visitWhileLoop(JCTree.JCWhileLoop tree) {
        if (tree.body != null) {
            maker.pos = tree.body.pos;
            List<JCTree.JCStatement> newStatements = getNewStatements(tree, getStatements(tree.body), false);
            tree.body = maker.Block(0, newStatements);
        }
        super.visitWhileLoop(tree);
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop tree) {
        if (tree.body != null) {
            maker.pos = tree.body.pos;
            List<JCTree.JCStatement> newStatements = getNewStatements(tree, getStatements(tree.body), false);
            tree.body = maker.Block(0, newStatements);
        }
        super.visitForLoop(tree);
    }
    
    List<JCTree.JCStatement> getStatements(JCTree.JCStatement statement) {
        return statement instanceof JCTree.JCBlock block ? block.getStatements() : List.of(statement);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        if (tree.body != null) {
            var allowFooter = BaseDafnyGenerator.isConstructor(tree.sym);
            tree.body.stats = getNewStatements(tree, tree.body.getStatements(), allowFooter);
        }
        super.visitMethodDef(tree);
    }

    private List<JCTree.JCStatement> getNewStatements(JCTree tree, List<JCTree.JCStatement> statements, boolean allowFooter) {
        var superOrThis = getSuperOrThis(statements);

        if (superOrThis != null) {
            statements = statements.tail;
        }

        java.util.List<JCTree.JCStatement> remainingStatements = new ArrayList<>(statements);
        var contractStatements = new ArrayList<JCTree.JCStatement>();
        remainingStatements = addHeaderContracts(remainingStatements, contractStatements);

        if (allowFooter) {
            remainingStatements = addFooterContracts(remainingStatements, contractStatements);
        }

        maker.pos = tree.pos;
        var contractBlock = maker.Block(0, List.from(contractStatements));

        if (superOrThis != null) {
            remainingStatements = new ArrayList<>(remainingStatements);
            remainingStatements.addFirst(superOrThis);
        }
        JCTree.JCBlock implementationBlock = maker.Block(0, List.from(remainingStatements));
        return List.<JCTree.JCStatement>of(implementationBlock).prepend(contractBlock);
    }

    private java.util.List<JCTree.JCStatement> addFooterContracts(java.util.List<JCTree.JCStatement> remainingStatements, ArrayList<JCTree.JCStatement> contractStatements) {
        int i;
        for (i = remainingStatements.size() - 1; i > 0; i--) {
            var current = remainingStatements.get(i);
            boolean foundHeader = isContractStatement(current);
            if (!foundHeader) {
                break;
            }
            contractStatements.add(current);
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
            java.util.List<JCTree.JCStatement> headerContracts) {
        int i;
        for (i = 0; i < statements.size(); i++) {
            var statement = statements.get(i);
            boolean isContract = isContractStatement(statement);
            if (!isContract) {
                break;
            }
            headerContracts.add(statement);
        }
        return statements.subList(headerContracts.size(), statements.size());
    }

    private boolean isContractStatement(JCTree.JCStatement statement) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        var jverifyMethod = BaseDafnyGenerator.getJVerifyMethod(invocation);
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

    public static JCTree.JCBlock getContractBlock(JCTree.JCMethodDecl tree) {
        return (JCTree.JCBlock) tree.body.getStatements().get(0);
    }


    public static boolean handleStatement(BaseDafnyGenerator compiler, JCTree.JCStatement statement, MethodOrLoopContract contract) {
        if (!(statement instanceof JCTree.JCExpressionStatement expressionStatement
                && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
            return false;
        }
        var jverifyMethod = BaseDafnyGenerator.getJVerifyMethod(invocation);
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
                Expression precondition = compiler.expressionCompiler.toExprWithFlows(invocation.getArguments().getFirst(), ExpressionContext.Pure).expression();
                contract.preconditions.add(new AttributedExpression(precondition, null, null));
            }
            case "postcondition" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A postcondition call may have only one argument");
                }
                handlePostcondition(compiler, contract, invocation.getArguments().getFirst());
            }
            case "invariant" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("invariant should have a single argument");
                }
                contract.loopInvariants.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst(), ExpressionContext.Pure), null, null));
            }
            case "decreases" -> {
                for(var decrease : invocation.getArguments()) {
                    // The LOWER javac phase inserts an explicit NewArray for varargs
                    if (decrease instanceof JCTree.JCNewArray newArray) {
                        contract.decreases.addAll(newArray.getInitializers().map(d -> compiler.expressionCompiler.toExpr(d, ExpressionContext.Pure)));
                    } else {
                        contract.decreases.add(compiler.expressionCompiler.toExpr(decrease, ExpressionContext.Pure));
                    }
                }
            }
            case "reads" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A reads call must have exactly one argument");
                }
                var origExpr = invocation.getArguments().getFirst();
                var origin = compiler.toOrigin(origExpr);
                var expr = compiler.expressionCompiler.toExpr(origExpr, ExpressionContext.Pure);
                contract.reads.add(new FrameExpression(origin, expr, null));
            }
            case "modifies" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A modifies call must have exactly one argument");
                }
                var origExpr = invocation.getArguments().getFirst();
                var origin = compiler.toOrigin(origExpr);
                var expr = compiler.expressionCompiler.toExpr(origExpr, ExpressionContext.Pure);
                contract.modifies.add(new FrameExpression(origin, expr, null));
            }
            default -> {
                compiler.reportError(invocation, "notSupported", methodName);
                return false;
            }
        }
        return true;
    }

    private static void handlePostcondition(BaseDafnyGenerator compiler, MethodOrLoopContract header, JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCLambda lambda) {
            if (lambda.getParameters().size() != 1) {
                throw new JavaViolationException("A postcondition call lambda must take exactly one argument");
            }
            var parameter = lambda.params.getFirst();
            var origin = compiler.toOrigin(lambda);
            var paramName = parameter.getName().toString();
            var type = compiler.getFinalGenerator().translateType(parameter.type, compiler.toOrigin(parameter), null);

            var returnVar = new BoundVar(origin, new Name(origin, paramName), type, false);
            var lhs = new CasePattern<>(origin, paramName, returnVar, null);
            var rhs = TreeInfo.isConstructor(header.treeOrigin)
                    ? new ThisExpr(origin)
                    : new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);
            var origCondition = compiler.expressionCompiler.toExpr((JCTree.JCExpression)lambda.getBody(), ExpressionContext.Pure);
            var condition = new LetExpr(origin, java.util.List.of(lhs), java.util.List.of(rhs), origCondition, true, null);
            header.postconditions.add(new AttributedExpression(condition, null, null));

        } else if (expr instanceof JCTree.JCMemberReference memberReference) {
            var origin = compiler.toOrigin(memberReference);
            NameSegment arg = new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);
            var callee = new ExprDotName(origin,
                    compiler.expressionCompiler.toExpr(memberReference.expr, ExpressionContext.Pure),
                    compiler.reporter.getName(memberReference, compiler.nameCompiler.getCompiledName(memberReference.sym, origin)), null);
            var call = ExpressionCompiler.createCall2(origin, callee, Stream.of(arg));
            header.postconditions.add(new AttributedExpression(call, null, null));
        } else if (expr instanceof JCTree.JCTypeCast typeCast) {
            // Casts like (IntPredicate) are sometimes necessary to disambiguate
            handlePostcondition(compiler, header, typeCast.getExpression());
        } else {
            var dafnyExpr = compiler.expressionCompiler.toExpr(expr, ExpressionContext.Pure);
            header.postconditions.add(new AttributedExpression(dafnyExpr, null, null));
        }
    }
}
