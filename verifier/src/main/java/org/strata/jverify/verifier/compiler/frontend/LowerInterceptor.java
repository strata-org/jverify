package org.strata.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;

/*
 * Contains several intercepting methods.
 * The lowercased suffix of the name of methods is used to determine what it is intercepting
 * If the name does not match anything, then the method is used to intercept the remainder of what needs
 * intercepting
 */
public class LowerInterceptor {
    static Field typesField;
    static Field makeField;
    private static final Field currentMethodDefField;
    private static final Field currentMethodSymField;

    static {
        try {
            makeField = Lower.class.getDeclaredField("make");
            typesField = Lower.class.getDeclaredField("types");
            currentMethodDefField = Lower.class.getDeclaredField("currentMethodDef");
            currentMethodSymField = Lower.class.getDeclaredField("currentMethodSym");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    Turns off type erasure for whatever it is intercepting
     */
    @RuntimeType
    public static Object intercept(@SuperCall Callable<Object> original,
                                   @This Object lowerInstance) throws Exception {
        TypesWithoutErasure types = (TypesWithoutErasure) typesField.get(lowerInstance);
        var previous = types.eraseTypes;
        types.eraseTypes = false;
        var result = original.call();
        types.eraseTypes = previous;
        return result;
    }

    /*
    Intercept the access method
     */
    public static JCTree.JCExpression interceptAccess(@SuperCall Callable<JCTree.JCExpression> original,
                                                      @This Object lowerInstance,
                                                      @Argument(0) Symbol sym,
                                                      @Argument(1) JCTree.JCExpression tree) throws Exception {
        JCTree.JCExpression result = original.call();

        if (sym.kind == Kinds.Kind.TYP && tree instanceof JCTree.JCTypeApply typeApply) {
            // The regular access method deconstructs JCTypeApply and does not reconstruct it, so we will do that here.
            TreeMaker make = (TreeMaker) makeField.get(lowerInstance);

            make.pos = tree.pos;

            JCTree.JCTypeApply newTypeApply = make.TypeApply(result, typeApply.arguments);
            newTypeApply.type = result.type;
            result = newTypeApply;
        }

        return result;
    }

    public static JCTree.JCExpression interceptVisitLambda(@SuperCall Callable<JCTree.JCExpression> original,
                                                      @This Object lowerInstance,
                                                      @Argument(0) JCTree.JCLambda lambda) throws Exception {
        var maker = (TreeMaker) makeField.get(lowerInstance);
        var types = (TypesWithoutErasure) typesField.get(lowerInstance);
        var samMethod = (Symbol.MethodSymbol)types.findDescriptorSymbol(lambda.type.tsym);
        var proxyMethod = maker.MethodDef(samMethod, null);

        JCTree.JCMethodDecl prevMethodDef = (JCTree.JCMethodDecl) currentMethodDefField.get(lowerInstance);
        Symbol.MethodSymbol prevMethodSym = (Symbol.MethodSymbol) currentMethodSymField.get(lowerInstance);
        try {
            currentMethodDefField.set(lowerInstance, proxyMethod);
            currentMethodSymField.set(lowerInstance, samMethod);
            return original.call();
        } finally {
            currentMethodDefField.set(lowerInstance, prevMethodDef);
            currentMethodSymField.set(lowerInstance, prevMethodSym);
        }
    }
}
