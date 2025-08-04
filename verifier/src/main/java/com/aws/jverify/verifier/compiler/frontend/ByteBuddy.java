package com.aws.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.Transformer;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ByteBuddy {

    public static void installModification() {
        try {
            // ByteBuddy handles the agent attachment automatically
            Instrumentation instrumentation = ByteBuddyAgent.install();

            new AgentBuilder.Default()
                    .type(named("com.sun.tools.javac.comp.Lower"))
                    .transform((builder, typeDescription, classLoader, module, _) ->
                            builder
                            // Make the 'make' field public
                            .field(named("make"))
                            .transform(Transformer.ForField.withModifiers(Visibility.PUBLIC))
                            .method(named("access").
                                            and(takesArgument(0, named("com.sun.tools.javac.code.Symbol"))))
                                    .intercept(MethodDelegation.to(LowerInterceptor.class))
                    )
                    .installOn(instrumentation);

        } catch (Exception e) {
            throw new RuntimeException("Failed to install Lower modification", e);
        }
    }
}

