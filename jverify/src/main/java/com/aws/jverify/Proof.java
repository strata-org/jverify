package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)  // Keep annotation at runtime
@Target(ElementType.METHOD)            // Can only be used on classes/interfaces
public @interface Proof {
}
