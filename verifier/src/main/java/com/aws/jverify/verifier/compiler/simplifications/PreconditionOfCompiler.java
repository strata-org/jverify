package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.JVerify;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.dafnygenerator.AttributedTreeCopier;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.source.tree.IdentifierTree;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Must run before lower
 */
public class PreconditionOfCompiler extends TreeTranslator {
    private final TreeMaker treeMaker;
    private final Reporter reporter;
    private final JVerifyIndex index;
    private final JVerifyUtils utils;
    private final Names names;
    private final MethodOrLoopContractCompiler contractCompiler;
    
    private final Symbol preconditionOf;

    public PreconditionOfCompiler(Context context) {
        names = Names.instance(context);
        treeMaker = TreeMaker.instance(context);
        reporter = Reporter.instance(context);
        utils = JVerifyUtils.instance(context);
        index = JVerifyIndex.instance(context);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        preconditionOf = utils.findSymbol(JVerify.class, "preconditionOf");
    }
    
    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation invocation) {
        var calledMethodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
        
        if (calledMethodSymbol != preconditionOf) {
            super.visitApply(invocation);
            return;
        }

        var call = invocation.args.getFirst();
        if (!(call instanceof JCTree.JCMethodInvocation preconditionOwnerCall)) {
            reporter.reportError(invocation, "preconditionArgumentMustBeCall");
            super.visitApply(invocation);
            return;
        }
        
        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(preconditionOwnerCall.getMethodSelect());
        var method = (JCTree.JCMethodDecl) index.getTree(methodSymbol);
        var contract = contractCompiler.getContract(method.body);
        if (contract.preconditions().size() != 1) {
            reporter.reportError(invocation, "preconditionOfTargetMustHaveSinglePrecondition");
            return;
        }
        var precondition = contract.preconditions().getFirst().get();

        JCTree.JCFieldAccess access = (JCTree.JCFieldAccess) preconditionOwnerCall.getMethodSelect();
        Map<Symbol.VarSymbol, JCTree.JCExpression> replacements = new HashMap<>();
        var params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            var arg = preconditionOwnerCall.args.get(i);
            replacements.put(param.sym, arg);
        }
        class VariableReplacer extends AttributedTreeCopier {
            public VariableReplacer(TreeMaker maker) {
                super(maker);
            }

            @Override
            public JCTree visitIdentifier(IdentifierTree node, Void p) {
                JCTree.JCIdent identifier = (JCTree.JCIdent) node;
                if (identifier.name == names._this) {
                    return access.getExpression();
                }
                var newValue = replacements.get(identifier.sym);
                if (newValue != null) {
                    return newValue;
                } else {
                    return super.visitIdentifier(node, p);
                }
            }
        }
        result = precondition.accept(new VariableReplacer(treeMaker), null);
    }
}
