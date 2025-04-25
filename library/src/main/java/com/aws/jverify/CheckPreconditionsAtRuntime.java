package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If used, any preconditions of this method will be translated to checks at runtime.
 * If a precondition check fails, a PreconditionFailure exception is thrown.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface CheckPreconditionsAtRuntime {
}
