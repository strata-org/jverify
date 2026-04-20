package org.strata.jverify.verifier.compiler.simplifications;

import org.strata.jverify.JVerify;
import org.strata.jverify.verifier.compiler.Reporter;
import org.strata.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.source.tree.IdentifierTree;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Handle 'preconditionOf' calls
 * <p>
 * A preconditionOf call contains a fully applied call to another method.
 * This compiler replaced the preconditionOf call with the precondition of the called method, 
 * with the arguments filled in.
 * <p>
 * Example:
 * class Foo {
 *   void foo(int x) {
 *       precondition(x > 10);
 *   }
 *   void bar(int x) {
 *       precondition(preconditionOf(foo(x + 1));
 *   }
 * }
 * Translates to
 * class Foo {
 *   void foo(int x) {
 *       precondition(x > 10);
 *   }
 *   void bar(int x) {
 *       precondition(x + 1 > 10);
 *   }
 * }
 * <p>
 * Must run before lower
 */
public class PreconditionOfCompiler extends TreeTranslator {
    private final TreeMaker treeMaker;
    private final Reporter reporter;
    private final JVerifyIndex index;
    private final JVerifyUtils utils;
    private final Symtab symtab;
    private final Context context;
    private final Names names;
    private final MethodOrLoopContractCompiler contractCompiler;
    
    private final Symbol preconditionOf;

    public PreconditionOfCompiler(Context context) {
        this.context = context;
        names = Names.instance(context);
        treeMaker = TreeMaker.instance(context);
        reporter = Reporter.instance(context);
        symtab = Symtab.instance(context);
        utils = JVerifyUtils.instance(context);
        index = JVerifyIndex.instance(context);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
        preconditionOf = utils.findSymbol(JVerify.class, "preconditionOf");
    }
    
    public java.util.List<JCTree.JCCompilationUnit> transform(java.util.List<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            reporter.compilationUnit = env;
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
            result = treeMaker.Literal(true);
            result.type = symtab.booleanType;
            return;
        }
        
        var methodSymbol = (Symbol.MethodSymbol) TreeInfo.symbol(preconditionOwnerCall.getMethodSelect());
        var method = (JCTree.JCMethodDecl) index.getTree(methodSymbol);
        var contract = contractCompiler.getContract(method.body);
        var precondition = MethodOrLoopContract.combineClauses(context, contract.preconditions());

        var fieldAccess = preconditionOwnerCall.getMethodSelect() instanceof JCTree.JCFieldAccess f ? f : null;
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
                if (fieldAccess != null && identifier.name == names._this) {
                    return fieldAccess.getExpression();
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
