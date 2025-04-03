package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Let the definitions in the class this annotation is attached to,
 * serve as a contract for the class this annotation references 
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Contract {
    Class<?> value();
}