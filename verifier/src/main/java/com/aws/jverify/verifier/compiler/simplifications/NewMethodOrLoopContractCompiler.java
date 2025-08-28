package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.ContractException;
import com.aws.jverify.Nullable;
import com.aws.jverify.common.Common;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.Reporter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.util.ArrayList;
import java.util.Set;

/*
Extracts contracts from constructor, method or loop bodies
Does not support lambdas.
 */
public class NewMethodOrLoopContractCompiler extends TreeTranslator {
    private final TreeMaker maker;
    private final Reporter reporter;
    private final JVerifyMaker jverifyMaker;

    private NewMethodOrLoopContractCompiler(Context context) {
        context.put(NewMethodOrLoopContractCompiler.class, this);
        this.maker = TreeMaker.instance(context);
        this.reporter = Reporter.instance(context);
        this.jverifyMaker = JVerifyMaker.instance(context);
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

    public static NewMethodOrLoopContractCompiler instance(Context context) {
        NewMethodOrLoopContractCompiler instance = context.get(NewMethodOrLoopContractCompiler.class);
        if (instance == null) {
            instance = new NewMethodOrLoopContractCompiler(context);
        }
        return instance;
    }
    
    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }
    
    public List<JCTree.JCStatement> extractContract(JavaToDafnyCompiler compiler, 
                                                              JCTree.JCBlock block,
                                                              MethodOrLoopContract contract) {
        if (block.getStatements().size() != 2) {
            throw new RuntimeException("Method body is not in contract + implementation format");
        }
        var contracts = (JCTree.JCBlock)block.getStatements().get(0);
        var implementation = block.getStatements().get(1);
        var hasImplementation = implementation instanceof JCTree.JCBlock;
        for(var contractStatement : contracts.getStatements()) {
            MethodOrLoopContractCompiler.handleStatement(compiler, contractStatement, contract);
        }

        List<JCTree.JCStatement> implementationStatements = hasImplementation 
                ? ((JCTree.JCBlock) implementation).getStatements() 
                : List.nil();
        if (contract.isPure) {
            if (!implementationStatements.isEmpty()) {
                contract.pureBody = compiler.expressionCompiler.toExpr(implementationStatements);
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
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        if (tree.body == null) {
            super.visitMethodDef(tree);
            return;
        }
        var allowFooter = JavaToDafnyCompiler.isConstructor(tree.sym);
        var statements = tree.body.getStatements();
        var superOrThis = getSuperOrThis(statements);

        if (superOrThis != null) {
            statements = statements.tail;
        }

        java.util.List<JCTree.JCStatement> remainingStatements = new ArrayList<>(statements);
        var contractStatements = new ArrayList<JCTree.JCStatement>();
        remainingStatements = addHeaderContracts(remainingStatements, contractStatements);
        
        if (allowFooter) {
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
        }
        
        maker.pos = tree.pos;
        var contractBlock = maker.Block(0, List.from(contractStatements));

        List<JCTree.JCStatement> implStatements;
        if (superOrThis != null) {
            remainingStatements = new ArrayList<>(remainingStatements);
            remainingStatements.addFirst(superOrThis);
        }
        JCTree.JCBlock implementationBlock = maker.Block(0, List.from(remainingStatements));
        implStatements = List.of(implementationBlock);
        tree.body.stats = implStatements.prepend(contractBlock);
        super.visitMethodDef(tree);
    }

    private static boolean hasImplementation(java.util.List<JCTree.JCStatement> remainingStatements) {
        boolean hasImplementation = true;
        if (remainingStatements.size() == 1) {
            var statement = remainingStatements.getFirst();
            boolean isContractThrow = statement instanceof JCTree.JCThrow throwStatement &&
                    throwStatement.expr.type.tsym.getQualifiedName().contentEquals(ContractException.class.getCanonicalName());
            hasImplementation = !isContractThrow;
        }
        return hasImplementation;
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
        JCTree.JCStatement implementation = jverifyMaker.contractThrow();
        return maker.Block(0, List.of(
                maker.Block(0, List.nil()),
                implementation));
    }

    public static JCTree.JCBlock getContractBlock(JCTree.JCMethodDecl tree) {
        return (JCTree.JCBlock) tree.body.getStatements().get(0);
    }
}
