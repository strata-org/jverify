package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import static com.sun.tools.javac.code.Flags.ABSTRACT;
import static com.sun.tools.javac.code.Flags.INTERFACE;

/**
 * This class is used to store important information regarding translation of
 * lambda expression/method references (see subclasses).
 */
abstract class TranslationContext<T extends JCTree.JCFunctionalExpression> {

    protected final LambdaAnalyzerPreprocessor lambdaAnalyzerPreprocessor;
    /**
     * the underlying (untranslated) tree
     */
    final T tree;

    /**
     * points to the adjusted enclosing scope in which this lambda/mref expression occurs
     */
    final Symbol owner;

    /**
     * the depth of this lambda expression in the frame stack
     */
    final int depth;

    /**
     * the enclosing translation context (set for nested lambdas/mref)
     */
    final TranslationContext<?> prev;

    /**
     * list of methods to be bridged by the meta-factory
     */
    final List<Symbol> bridges;

    TranslationContext(LambdaAnalyzerPreprocessor lambdaAnalyzerPreprocessor, T tree) {
        this.lambdaAnalyzerPreprocessor = lambdaAnalyzerPreprocessor;
        this.tree = tree;
        this.owner = lambdaAnalyzerPreprocessor.owner(true);
        this.depth = lambdaAnalyzerPreprocessor.frameStack.size() - 1;
        this.prev = lambdaAnalyzerPreprocessor.context();
        Symbol.ClassSymbol csym =
                lambdaAnalyzerPreprocessor.lambdaCompiler.types.makeFunctionalInterfaceClass(lambdaAnalyzerPreprocessor.lambdaCompiler.attrEnv, lambdaAnalyzerPreprocessor.lambdaCompiler.names.empty, tree.target, ABSTRACT | INTERFACE);
        this.bridges = lambdaAnalyzerPreprocessor.lambdaCompiler.types.functionalInterfaceBridges(csym);
    }

    /**
     * does this functional expression need to be created using alternate metafactory?
     */
    boolean needsAltMetafactory() {
        return tree.target.isIntersection() ||
                bridges.length() > 1;
    }

    /**
     * @return Name of the enclosing method to be folded into synthetic
     * method name
     */
    String enclosingMethodName() {
        return syntheticMethodNameComponent(owner.name);
    }

    /**
     * @return Method name in a form that can be folded into a
     * component of a synthetic method name
     */
    String syntheticMethodNameComponent(Name name) {
        if (name == null) {
            return "null";
        }
        String methodName = name.toString();
        if (methodName.equals("<clinit>")) {
            methodName = "static";
        } else if (methodName.equals("<init>")) {
            methodName = "new";
        }
        return methodName;
    }
}
