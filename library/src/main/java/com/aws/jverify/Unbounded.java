package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
The annotated numeric variable can not overflow.
Note that this prevents the containing method from being executed at runtime
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE})
public @interface Unbounded {
}
