package org.strata.jverify.contracts2jqwik;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import org.strata.jverify.common.Common;

/**
 * Extracts {@code precondition} and {@code postcondition} call expressions from
 * a method body. The extraction is purely structural — clauses are identified
 * by name, and their argument expressions are returned without modification.
 *
 * <p>Only top-level {@code precondition}/{@code postcondition} statements in
 * the method's outermost block are recognized. Calls inside nested blocks
 * (loops, conditionals, lambdas) are intentionally ignored — contracts are
 * expected at the entry of the method, matching how {@code @AsProperty} is used.</p>
 */
final class ContractClauses {

    /** Simple-name match for the JVerify postcondition method. */
    private static final String POSTCONDITION = "postcondition";

    final List<ClauseTranslator.Clause> preconditions;
    /** All postconditions in source order; conjoined with {@code &&} during translation. */
    final List<ClauseTranslator.Clause> postconditions;

    private ContractClauses(List<ClauseTranslator.Clause> preconditions,
                            List<ClauseTranslator.Clause> postconditions) {
        this.preconditions = preconditions;
        this.postconditions = postconditions;
    }

    static ContractClauses extract(JCTree.JCBlock body) {
        ListBuffer<ClauseTranslator.Clause> pres = new ListBuffer<>();
        ListBuffer<ClauseTranslator.Clause> posts = new ListBuffer<>();

        for (JCTree.JCStatement stmt : body.stats) {
            if (!(stmt instanceof JCTree.JCExpressionStatement exprStmt)) {
                continue;
            }
            if (!(exprStmt.expr instanceof JCTree.JCMethodInvocation invocation)) {
                continue;
            }
            String methodName = methodName(invocation);
            if (methodName == null) {
                continue;
            }
            if (methodName.equals(Common.PRECONDITION) && !invocation.args.isEmpty()) {
                pres.append(new ClauseTranslator.Clause(invocation.args.head));
            } else if (methodName.equals(POSTCONDITION) && !invocation.args.isEmpty()) {
                posts.append(new ClauseTranslator.Clause(invocation.args.head));
            }
        }

        return new ContractClauses(pres.toList(), posts.toList());
    }

    private static String methodName(JCTree.JCMethodInvocation invocation) {
        JCTree.JCExpression select = invocation.meth;
        if (select instanceof JCTree.JCIdent ident) {
            return ident.name.toString();
        }
        if (select instanceof JCTree.JCFieldAccess fieldAccess) {
            return fieldAccess.name.toString();
        }
        // Fall back to TreeInfo if the simple cases miss.
        var sym = TreeInfo.symbol(select);
        return sym == null ? null : sym.name.toString();
    }
}
