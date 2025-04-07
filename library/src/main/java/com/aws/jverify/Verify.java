package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specify whether this symbol should be verified or not
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PACKAGE})
public @interface Verify {
    /**
     * Whether this symbol should be verified or not
     */
    boolean value() default true;
    /**
     * Whether this annotation should apply recursively to members of this symbol 
     */
    boolean members() default true;
}

