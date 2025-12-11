package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.verifier.compiler.Reporter;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;

import java.util.List;

public record MethodOrLoopContract(
        List<Property<JCTree.JCExpression>> preconditions,
        List<Property<JCTree.JCExpression>> postconditions,
        List<Property<JCTree.JCExpression>> loopInvariants,
        List<JCTree.JCExpression> decreases,
        List<Property<JCTree.JCExpression>> reads,
        List<Property<JCTree.JCExpression>> modifies) {
    
    public static JCTree.JCExpression combineClauses(Context context, 
                                                     List<Property<JCTree.JCExpression>> clauses) {
        var treeMaker = TreeMaker.instance(context);
        Attr attr = Attr.instance(context);
        Enter enter = Enter.instance(context);
        Reporter reporter = Reporter.instance(context);

        return clauses.stream().map(Property::get).
                reduce((a,b) -> {
// We need to build an attributed binary with the arguments a ane b.
// Both 'a' and 'b' are already attributed, but if we build binary with them and attribute it, then
// a and b will be re-attributed. This can fail unless we provide the right environment,
// think of the method scope.
// To make attribution of Binary easier, we supply temporary literal arguments to binary first
// since these don't need anything from the method scope.
                    var trueLiteral = treeMaker.Literal(true);
                    JCTree.JCBinary binary = treeMaker.Binary(JCTree.Tag.AND, trueLiteral, trueLiteral);
                    Env<AttrContext> env = enter.getTopLevelEnv(reporter.compilationUnit);
                    attr.attribExpr(binary, env, Type.noType);
                    // Replace temporary arguments
                    binary.lhs = a;
                    binary.rhs = b;
                    return binary;
                }).
                orElse(treeMaker.Literal(true));
    }
}
