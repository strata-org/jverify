package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.List;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.Transformer;
import net.bytebuddy.implementation.MethodDelegation;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.*;

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
                            .method(named("access").
                                    and(takesArgument(0, named("com.sun.tools.javac.code.Symbol"))))
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                            .method(named("freevarDefs").
                                            and(takesArguments(int.class, List.class, Symbol.class, long.class)))
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                    )
                    .installOn(instrumentation);

        } catch (Exception e) {
            throw new RuntimeException("Failed to install Lower modification", e);
        }
    }
}

