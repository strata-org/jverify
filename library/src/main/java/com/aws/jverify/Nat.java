package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
The annotated variable must be a natural number, i.e. >= 0
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
public @interface Nat {
}
