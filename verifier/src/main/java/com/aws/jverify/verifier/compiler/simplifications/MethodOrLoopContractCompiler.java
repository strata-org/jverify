package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.aws.jverify.common.Common;
import com.aws.jverify.verifier.compiler.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
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

    public static List<JCTree.JCStatement> getImplementationStatements(JCTree.JCStatement outerBlock) {
        var implementationBlock = getImplementationBlock(outerBlock);
        return implementationBlock == null ? List.nil() : implementationBlock.getStatements();
    }
    
    public static JCTree.@Nullable JCBlock getImplementationBlock(JCTree.JCStatement outerBlock) {
        JCTree.JCStatement second = ((JCTree.JCBlock)outerBlock).getStatements().get(1);
        if (second instanceof JCTree.JCBlock block) {
            return block;
        }
        return null;
    }
    
    public static boolean hasImplementation(JCTree.JCMethodDecl method) {
        return method.body != null && method.body.getStatements().get(1) instanceof JCTree.JCBlock;
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
            var allowFooter = JVerifyUtils.isConstructor(tree.sym);
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

        if (superOrThis != null) {
            remainingStatements = new ArrayList<>(remainingStatements);
            remainingStatements.addFirst(superOrThis);
        }
        return getOuterBlockStatements(contractStatements, remainingStatements);
    }

    public List<JCTree.JCStatement> getOuterBlockStatements(java.util.List<JCTree.JCStatement> contractStatements, java.util.List<JCTree.JCStatement> remainingStatements) {
        var contractBlock = maker.Block(0, List.from(contractStatements));
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
            case "check", "assume" -> {
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
    
    public static JCTree.JCBlock getContractBlock(JCTree.JCStatement outerBlock) {
        return (JCTree.JCBlock) ((JCTree.JCBlock)outerBlock).getStatements().get(0);
    }
    
    public MethodOrLoopContract getContract(JCTree.JCBlock outerBlock) {
        var contractBlock = getContractBlock(outerBlock);
        Property<JCTree.JCExpression> trueProperty = Property.finalProperty(maker.Literal(true));
        Property<JCTree.JCExpression> precondition = trueProperty;
        Property<JCTree.JCExpression> postcondition = trueProperty;
        ArrayList<JCTree.JCExpression> decreases = new ArrayList<>();
        Property<JCTree.JCExpression> nullProperty = Property.finalProperty(null);
        Property<JCTree.JCExpression> loopInvariant = nullProperty;
        Property<JCTree.JCExpression> reads = nullProperty;
        Property<JCTree.JCExpression> modifies = nullProperty;
                
        for(var contractStatement : contractBlock.getStatements()) {
            if (!((JCTree.JCStatement) contractStatement instanceof JCTree.JCExpressionStatement expressionStatement
                    && expressionStatement.getExpression() instanceof JCTree.JCMethodInvocation invocation)) {
                continue;
            }
            var jverifyMethod = BaseDafnyGenerator.getJVerifyMethod(invocation);
            if (jverifyMethod == null) {
                continue;
            }
            var methodName = jverifyMethod.getQualifiedName().toString();
            switch (methodName) {
                case "check", "assume" -> {
                    // not a header method, so stop here
                    continue;
                }
                case Common.PRECONDITION -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A precondition call may have only one argument");
                    }
                    precondition = Property.fromElement(invocation.getArguments(), 0);
                }
                case "postcondition" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A postcondition call may have only one argument");
                    }
                    postcondition = Property.fromElement(invocation.getArguments(), 0);
                }
                case "invariant" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("invariant should have a single argument");
                    }
                    loopInvariant = Property.fromElement(invocation.getArguments(), 0);
                }
                case "decreases" -> {
                    for(var decrease : invocation.getArguments()) {
                        // The LOWER javac phase inserts an explicit NewArray for varargs
                        if (decrease instanceof JCTree.JCNewArray newArray) {
                            decreases.addAll(newArray.getInitializers());
                        } else {
                            decreases.add(decrease);
                        }
                    }
                }
                case "reads" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A reads call must have exactly one argument");
                    }
                    reads = Property.fromElement(invocation.getArguments(), 0);
                }
                case "modifies" -> {
                    if (invocation.args.size() != 1) {
                        throw new JavaViolationException("A modifies call must have exactly one argument");
                    }
                    modifies = Property.fromElement(invocation.getArguments(), 0);
                }
                default -> {
                    reporter.reportError(invocation, "notSupported", methodName);
                }
            }
        }
        return new MethodOrLoopContract(precondition, postcondition, 
                loopInvariant, decreases, reads, modifies);
    }
}
