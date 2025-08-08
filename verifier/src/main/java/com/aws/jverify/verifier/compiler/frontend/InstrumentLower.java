package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.Transformer;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * We override methods in Lower.java that create VarSymbol s
 * so that they will not erase the assigned type.
 * <p>
 * We can not stop doing type erasure in Lower.java in general,
 * because it depends on erasure for type comparisons and lookups.
 * <p>
 * Java module restrictions prevent us from changing the accessibility of Lower fields through reflection
 * so we change the visibility of some fields through bytecode replacement
 */
public class InstrumentLower {

    public static void installModification() {
        try {
            Instrumentation instrumentation = ByteBuddyAgent.install();

            new AgentBuilder.Default()
                    .type(is(Lower.class))
                    .transform((builder, typeDescription, classLoader, module, _) ->
                            builder
                            .field(named("make"))
                                .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))
                            .field(named("types"))
                                .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))
                            .method(named("access").
                                    and(takesArgument(0, is(Symbol.class))))
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                            .method(named("freevarDefs").
                                    and(takesArguments(int.class, List.class, Symbol.class, long.class)))
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                            .method(named("visitTypeTest")
                                    // The first call to types.erasure in visitEnumDef,
                                    // part of assigning the variable 'e_class',
                                    // should not be erased, because it's used as part of a type based lookup
                                    // However, the other erasure might be good to skip
                                    // We could intercept visitEnumDef and only skip the first erasure call
                                    //.or(named("visitEnumDef"))
                            )
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                    )
                    .installOn(instrumentation);

        } catch (Exception e) {
            throw new RuntimeException("Failed to install Lower modification", e);
        }
    }
    
    /*
     * Contains several intercepting methods.
     * The lowercased suffix of the name of methods is used to determine what it is intercepting
     * If the name does not match anything, then the method is used to intercept the remainder of what needs
     * intercepting
     */
    public static class LowerInterceptor {
        static Field typesField;
        static Field makeField;

        static {
            try {
                makeField = Lower.class.getDeclaredField("make");
                typesField = Lower.class.getDeclaredField("types");
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
    }
}

