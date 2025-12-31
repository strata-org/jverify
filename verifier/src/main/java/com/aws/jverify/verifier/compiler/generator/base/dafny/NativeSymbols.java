package com.aws.jverify.verifier.compiler.generator.base.dafny;

import com.aws.jverify.generated.Expression;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.aws.jverify.generated.IOrigin;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry of native Java symbols (Math, Double methods/fields) that have special
 * Dafny translations. This centralizes the handling of these symbols so other
 * compilation passes can check if a symbol is native and skip processing it.
 */
public class NativeSymbols {
    private static final Context.Key<NativeSymbols> KEY = new Context.Key<>();

    private final Map<Symbol.MethodSymbol, MethodTranslator> methodTranslators = new HashMap<>();
    private final Map<Symbol.VarSymbol, FieldTranslator> fieldTranslators = new HashMap<>();

    @FunctionalInterface
    public interface MethodTranslator {
        Expression translate(JCTree.JCMethodInvocation invocation, IOrigin origin, ExpressionContext context);
    }

    @FunctionalInterface
    public interface FieldTranslator {
        Expression translate(JCTree.JCFieldAccess fieldAccess, IOrigin origin);
    }

    private NativeSymbols() {
    }

    public static NativeSymbols instance(Context context) {
        NativeSymbols instance = context.get(KEY);
        if (instance == null) {
            instance = new NativeSymbols();
            context.put(KEY, instance);
        }
        return instance;
    }

    public void registerMethod(Symbol.MethodSymbol symbol, MethodTranslator translator) {
        methodTranslators.put(symbol, translator);
    }

    public void registerField(Symbol.VarSymbol symbol, FieldTranslator translator) {
        fieldTranslators.put(symbol, translator);
    }

    public boolean isRegistered(Symbol symbol) {
        if (symbol instanceof Symbol.MethodSymbol ms) {
            return methodTranslators.containsKey(ms);
        }
        if (symbol instanceof Symbol.VarSymbol vs) {
            return fieldTranslators.containsKey(vs);
        }
        return false;
    }

    public Expression translateMethod(Symbol.MethodSymbol symbol, JCTree.JCMethodInvocation invocation, 
                                     IOrigin origin, ExpressionContext context) {
        MethodTranslator translator = methodTranslators.get(symbol);
        if (translator == null) {
            return null;
        }
        return translator.translate(invocation, origin, context);
    }

    public Expression translateField(Symbol.VarSymbol symbol, JCTree.JCFieldAccess fieldAccess, IOrigin origin) {
        FieldTranslator translator = fieldTranslators.get(symbol);
        if (translator == null) {
            return null;
        }
        return translator.translate(fieldAccess, origin);
    }
}
