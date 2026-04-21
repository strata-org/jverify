package org.strata.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
Enables this method to be called inside contracts

However, puts restrictions on what the method can do:
- The method may not modify any existing objects.
- The method may only contain non-mutating statements
 
 The @Pure annotation is always inherited onto overriding methods 
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Pure {}
