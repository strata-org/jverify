package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When used on variables of type Object, allows assigning pure types to this variable.
 * However, prevent using references equality with such a variable.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE_USE})
public @interface PureRef {
}
