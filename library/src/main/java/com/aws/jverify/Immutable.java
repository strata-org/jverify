package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables constructing this class in pure code
 * 
 * Must only be used on classes that are immutable and
 * whose hashcode and equals implementation define structural equality.
 * 
 * Only usable on classes defined in libraries. 
 * For source code, use a record instead of a class
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
public @interface Immutable {
}
