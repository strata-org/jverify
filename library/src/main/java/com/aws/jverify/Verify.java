package com.aws.jverify;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specify whether this symbol should be verified or not
 */
@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PACKAGE})
public @interface Verify {
    /**
     * Whether this symbol and nested symbols should be verified or not
     */
    boolean value() default true;
    /**
     * Whether this annotation overrides any @Verify annotations applied on nested symbols 
     */
    boolean overrideChildren() default false;
}

