package org.strata.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compiles this method to a transparent Laurel procedure, so callers and contracts see
 * its body rather than only its contract.
 *
 * <p>A transparent method may not declare postconditions or a {@code modifies} clause.
 * Everything else about its body is checked by the Strata back-end, which reports any
 * construct it does not yet accept in a transparent body.
 *
 * <p>The annotation is not inherited: an overriding method is transparent only if it is
 * annotated itself.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Transparent {}
