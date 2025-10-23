package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.Transformer;
import net.bytebuddy.implementation.MethodDelegation;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * Since this class is intercepting the loading of the Lower class, it must not import it.
 * <p>
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
                    .type(named("com.sun.tools.javac.comp.Lower"))
                    .transform((builder, typeDescription, classLoader, module, _) ->
                            builder
                            .field(named("make"))
                                .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))
                            .field(named("types"))
                                .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))
                            .field(named("attrEnv"))
                                .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))
                            .field(named("currentMethodSym"))
                                .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))

                            .method(named("visitForeachLoop").
                                    and(takesArgument(0, is(JCTree.JCEnhancedForLoop.class))))
                            .intercept(MethodDelegation.to(LowerInterceptor.class))
                            .method(named("access").
                                    and(takesArgument(0, is(Symbol.class))))
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                            .method(named("makeOuterThisVarSymbol"))
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
    
}

