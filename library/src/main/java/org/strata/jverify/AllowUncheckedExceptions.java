package org.strata.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Whether unchecked exceptions ({@link RuntimeException}, {@link Error}) may leave
 * the annotated methods without being declared in their {@code throws} clause.
 *
 * <p>When allowed, a method's thrown type is inferred from its body: every exception
 * that may escape it uncaught, joined with any declared one. A caller then has to
 * handle the exception, or allow it in turn. When not allowed (the default), an
 * undeclared exception that may escape is a verification error at the throw.
 *
 * <p>The innermost annotation applies: a method's wins over its class's, which wins
 * over its package's, which wins over the verifier's
 * {@code --allow-unchecked-exceptions} option. An annotation with
 * {@code override = true} instead applies to everything inside it, whatever those
 * elements say, so {@code @AllowUncheckedExceptions(allow = false, override = true)}
 * on a package forbids undeclared unchecked exceptions throughout it.
 *
 * <p>A method's thrown type comes from its own body, never from its overrides. A
 * method without a body (an interface or abstract method) has nothing to infer
 * from, so it throws {@code RuntimeException}, joined with its declared type. An
 * override is capped at the type of the method it overrides, as a checked
 * {@code throws} is in Java: an exception it may throw beyond that type is still
 * asserted not to happen, so an override of a method that throws nothing gains
 * nothing from the annotation. An override may throw less than its base, or nothing.
 *
 * <p>Limitations: constructors are not covered, so an exception a constructor may
 * throw must still be ruled out. An implicit exception (a division by zero, a failing
 * cast) is only inferred where it is unconditionally evaluated before any call in its
 * statement; under a ternary arm, the right operand of {@code &&}/{@code ||}, a loop
 * header, or after a call, it is still asserted not to happen. A method with
 * {@code throwsOn} cases is not covered: its cases are stated for its declared
 * type, so an unchecked exception it may throw is still asserted not to happen.
 * Neither is an override of a library method, nor a method that a subclass inherits
 * as the implementation of an interface method.
 *
 * <p>An inferred type says which exceptions a method may throw, not when: a division
 * counts even when a precondition rules out a zero divisor. A caller that is not
 * covered must therefore catch the exception, even if its arguments rule it out.
 *
 * <p>A call to an inferred method is subject to the same limits as a call to a method
 * that declares a checked exception: it is rejected inside a larger expression (as in
 * {@code div(a, b) + 1}), and a {@code catch} around a call on an object (as in
 * {@code o.m(b)}) is rejected too. Both stop verification of the whole program.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD})
public @interface AllowUncheckedExceptions {
    /** Whether undeclared unchecked exceptions may escape. */
    boolean allow() default true;

    /** Whether this annotation also applies to nested elements that carry their own. */
    boolean override() default false;
}
