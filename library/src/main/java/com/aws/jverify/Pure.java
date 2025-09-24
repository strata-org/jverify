package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can be used on:
 * - Method
 * - Types, although this is mostly used on interfaces
 * - Variable and fields of type Object
 * <p>
 * When used on methods, enables this method to be called inside contracts.
 * <p>
 * However, puts restrictions on what the method can do:
 * - The method may not modify any existing objects.
 * - The method may only contain non-mutating statements
 * <p>
 * The @Pure annotation is always inherited onto overriding methods
 * <p>
 * When used on an interface, makes this interface pure, 
 * meaning it can be implemented by a pure type such as a record.
 * However, this prevents using reference equality on types of that interface
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Pure {
}
