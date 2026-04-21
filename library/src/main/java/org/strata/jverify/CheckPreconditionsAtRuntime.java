package org.strata.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If used, any preconditions of this method will be translated to checks at runtime.
 * If a precondition check fails, a PreconditionFailure exception is thrown.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CheckPreconditionsAtRuntime {
}
