package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.ContractException;
import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.ArrayList;
import java.util.List;

/*
Extracts contracts from constructor, method or loop bodies
 */
public class MethodOrLoopContractCompiler {
    public final JavaToDafnyCompiler compiler;

    public MethodOrLoopContractCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    
    public List<JCTree.JCStatement> extractContract(JCTree.JCStatement statement,
                                                    MethodOrLoopContract contract,
                                                    boolean allowFooter) {
        var statements = statement instanceof JCTree.JCBlock block
                ? block.getStatements()
                : List.of(statement);
        return extractContract(statements, contract, allowFooter);
    }
    
    public List<JCTree.JCStatement> extractContract(List<JCTree.JCStatement> statements,
                                                    MethodOrLoopContract contract,
                                                    boolean allowFooter) {
        var superOrThis = getSuperOrThis(statements);
        
        var remainingStatements = statements;
        if (superOrThis != null) {
            remainingStatements = remainingStatements.subList(1, statements.size());
        }
        remainingStatements = addHeaderContracts(remainingStatements, contract);
        if (allowFooter) {
            remainingStatements = addFooterContracts(remainingStatements, contract);
        }
        var result = new ArrayList<>(remainingStatements);
        if (superOrThis != null) {
            result.addFirst(superOrThis);
        }

        if (contract.isPure) {
            if (result.size() == 1) {
                var statement = result.getFirst();
                var bodyless = statement instanceof JCTree.JCThrow throwStatement &&
                        throwStatement.expr.type.tsym.getQualifiedName().contentEquals(ContractException.class.getCanonicalName());
                if (bodyless) {
                    return result;
                }
            }

            contract.pureBody = compiler.expressionCompiler.toExpr(remainingStatements);
            return List.of();
        }
        return result;
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

    private List<JCTree.JCStatement> addHeaderContracts(List<JCTree.JCStatement> statements,
                                                        MethodOrLoopContract contract) {
        int headerContracts;
        int i;
        for (i = 0; i < statements.size(); i++) {
            var statement = statements.get(i);
            boolean foundHeader = handleStatement(statement, contract);
            if (!foundHeader) {
                break;
            }
        }
        headerContracts = i;
        return statements.subList(headerContracts, statements.size());
    }

    private List<JCTree.JCStatement> addFooterContracts(List<JCTree.JCStatement> statements, 
                                                        MethodOrLoopContract contract) {
        int i;
        for (i = statements.size() - 1; i > 0; i--) {
            var statement = statements.get(i);
            boolean foundHeader = handleStatement(statement, contract);
            if (!foundHeader) {
                break;
            }
        }
        int footerContracts = i + 1;
        return statements.subList(0, footerContracts);
    }

    private boolean handleStatement(JCTree.JCStatement statement, MethodOrLoopContract contract) {
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
                contract.preconditions.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst()), null, null));
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
                contract.invariants.add(new AttributedExpression(compiler.expressionCompiler.toExpr(invocation.getArguments().getFirst()), null, null));
            }
            case "decreases" -> {
                for(var decrease : invocation.getArguments()) {
                    // The LOWER javac phase inserts an explicit NewArray for varargs
                    if (decrease instanceof JCTree.JCNewArray newArray) {
                        contract.decreases.addAll(newArray.getInitializers().map(compiler.expressionCompiler::toExpr));
                    } else {
                        contract.decreases.add(compiler.expressionCompiler.toExpr(decrease));
                    }
                }
            }
            case "reads" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A reads call must have exactly one argument");
                }
                var origExpr = invocation.getArguments().getFirst();
                var origin = compiler.toOrigin(origExpr);
                var expr = compiler.expressionCompiler.toExpr(origExpr);
                contract.reads.add(new FrameExpression(origin, expr, null));
            }
            case "modifies" -> {
                if (invocation.args.size() != 1) {
                    throw new JavaViolationException("A modifies call must have exactly one argument");
                }
                var origExpr = invocation.getArguments().getFirst();
                var origin = compiler.toOrigin(origExpr);
                var expr = compiler.expressionCompiler.toExpr(origExpr);
                contract.modifies.add(new FrameExpression(origin, expr, null));
            }
            default -> {
                compiler.reportError(invocation, "notSupported", methodName);
                return false;
            }
        }
        return true;
    }

    private void handlePostcondition(MethodOrLoopContract header, JCTree.JCExpression expr) {
        if (expr instanceof JCTree.JCLambda lambda) {
            if (lambda.getParameters().size() != 1) {
                throw new JavaViolationException("A postcondition call lambda must take exactly one argument");
            }
            var parameter = lambda.params.getFirst();
            var origin = compiler.toOrigin(lambda);
            var paramName = parameter.getName().toString();
            var type = compiler.translateType(parameter.type, compiler.toOrigin(parameter), null);

            var returnVar = new BoundVar(origin, new Name(origin, paramName), type, false);
            var lhs = new CasePattern<>(origin, paramName, returnVar, null);
            var rhs = TreeInfo.isConstructor(header.treeOrigin)
                    ? new ThisExpr(origin)
                    : new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);
            var origCondition = compiler.expressionCompiler.toExpr(lambda.getBody());
            var condition = new LetExpr(origin, List.of(lhs), List.of(rhs), origCondition, true, null);
            header.postconditions.add(new AttributedExpression(condition, null, null));

        } else if (expr instanceof JCTree.JCMemberReference memberReference) {
            var origin = compiler.toOrigin(memberReference);
            var argBindings = List.of(new ActualBinding(null,
                    new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null), false));
            var callee = new ExprDotName(origin,
                    compiler.expressionCompiler.toExpr(memberReference.expr),
                    compiler.getName(memberReference, compiler.nameCompiler.getCompiledName(memberReference.sym, origin)), null);
            var call = new ApplySuffix(origin, callee, null,
                    new ActualBindings(argBindings), null);
            header.postconditions.add(new AttributedExpression(call, null, null));
        } else if (expr instanceof JCTree.JCTypeCast typeCast) {
            // Casts like (IntPredicate) are sometimes necessary to disambiguate
            handlePostcondition(header, typeCast.getExpression());
        } else {
            var dafnyExpr = compiler.expressionCompiler.toExpr(expr);
            header.postconditions.add(new AttributedExpression(dafnyExpr, null, null));
        }
    }
}
