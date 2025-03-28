package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes this method, which either implements an interface method,
 * or overrides a method from a superclass, to inherit its contract
 * from the base method.
 * The contract may be extended as long as it only becomes stronger:
 * additional postconditions can be specified, but
 * no additional preconditions or reads or modifies.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface InheritContract {
}
