package com.aws.jverify.verifier;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.javac.code.Flags.SYNTHETIC;
import static com.sun.tools.javac.code.Flags.NATIVE;

/**
 * A translator that substitutes calls to JVerify library methods
 * with placeholders of the form $jverifyPlaceholder(n), or the reverse substitution
 * to put the library calls back in the same places.
 *
 * Used to temporarily remove these calls so that the JVerify library methods
 * are not rewritten by translators like LambdaToMethod.
 * These library methods often use lambda expressions, e.g. in
 * postcondition((Integer result) -> ...) or forall((Integer x) -> ...).
 * Having them rewritten would make them harder to translate into Dafny,
 * so instead we exclude them from the rewriting.
 * For simplicity, we just hide any JVerify method and not just those that use lambdas.
 */
public class ErasedCodeSubstituter extends TreeTranslator {

    protected static final Context.Key<ErasedCodeSubstituter> substituterKey = new Context.Key<>();

    private final Map<Integer, JCTree> substitutions = new HashMap<>();

    private final Context context;
    private final TreeMaker maker;
    private final Names names;
    private final Name placeholderName;
    private JCTree.JCExpression placeholderMethod;

    public static ErasedCodeSubstituter instance(Context context) {
        ErasedCodeSubstituter instance = context.get(substituterKey);
        if (instance == null)
            instance = new ErasedCodeSubstituter(context);
        return instance;
    }

    public ErasedCodeSubstituter(Context context) {
        context.put(substituterKey, this);
        this.context = context;
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.placeholderName = names.fromString("$jverifyPlaceholder");
    }

    private JCTree.JCExpression getPlaceholderMethod(JCTree.JCMethodInvocation invocation) {
        if (placeholderMethod == null) {
            var symtab = Symtab.instance(context);
            var methodType = new Type.MethodType(List.of(symtab.intType), symtab.voidType, List.nil(), symtab.noSymbol);
            var owner = ((JCTree.JCIdent)invocation.getMethodSelect()).sym.owner;
            var symbol = new Symbol.MethodSymbol(SYNTHETIC | NATIVE, placeholderName, methodType, owner);
            this.placeholderMethod = maker.Ident(symbol);
        }
        return placeholderMethod;
    }

    private Integer getPlaceholderInvokeID(JCTree.JCMethodInvocation invocation) {
        var select = invocation.getMethodSelect();
        if (select instanceof JCTree.JCIdent ident && ident.name.equals(placeholderName)) {
            return (Integer)((JCTree.JCLiteral)invocation.args.getFirst()).value;
        }
        return null;
    }

    private enum Direction {
        SUBSTITUTE,
        UNSUBSTITUTE
    }
    private Direction direction;

    public <T extends JCTree> T substitute(T tree) {
        direction = Direction.SUBSTITUTE;
        T result = translate(tree);
        direction = null;
        return result;
    }

    public <T extends JCTree> T unsubstitute(T tree) {
        direction = Direction.UNSUBSTITUTE;
        T result = translate(tree);
        direction = null;
        return result;
    }

    @Override
    public void visitApply(JCTree.JCMethodInvocation invocation) {
        switch (direction) {
            case SUBSTITUTE -> {
                var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
                if (jverifyMethod != null) {
                    var id = substitutions.size();
                    result = maker.App(getPlaceholderMethod(invocation), List.of(maker.Literal(id)));
                    substitutions.put(id, invocation);
                } else {
                    super.visitApply(invocation);
                }
            }
            case UNSUBSTITUTE -> {
                var id = getPlaceholderInvokeID(invocation);
                if (id != null) {
                    result = substitutions.remove(id);
                } else {
                    super.visitApply(invocation);
                }
            }
        }
    }
}
