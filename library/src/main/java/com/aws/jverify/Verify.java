package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specify whether this symbol should be verified or not
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
public @interface Verify {
    /**
     * Whether this symbol and nested symbols should be verified or not
     */
    boolean value() default true;
    /**
     * Whether this annotation overrides any @Verify annotations applied on nested symbols 
     */
    boolean overrideChildren() default false;
    
    /*
    Enables hiding the body of pure methods, so they do not need to be pure
    Only possible if this method is not verified
     */
    boolean hidePureBodies() default false;
}