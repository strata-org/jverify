package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can be used on a variable of type Object, 
 * to allow assigning values with a record type, or a generic type, to this variable.
 * However, the `==` operator can not be used on immutable objects.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
public @interface Immutable {
}
