package org.strata.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an entry point for JVerify's concrete interpretation mode
 * ({@code --interpret}).
 *
 * <p>Unlike verification, which reasons about all inputs symbolically,
 * interpretation concretely executes a chosen entry point with the values in
 * the source, treating contracts as runtime assertions. Annotating a method
 * with {@code @InterpretEntry} lets the <em>producer</em> declare which
 * procedure to run, so the interpreter finds it via Laurel/Core metadata rather
 * than by reconstructing a mangled procedure name.
 *
 * <p>The annotated method should be effectively no-argument (it sets up its own
 * inputs). It has no effect on verification.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface InterpretEntry {}
