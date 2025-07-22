package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Can be used on an interface declaration. In that case,
 * enables using this interface in reads and modifies clauses.
 * But prevents letting a record inherit from it.
 * <p>
 * Can also be used on the type Object, 
 * to allow using the `==` operator with it.
 * However, this will prevent assigning values with a record type, or a generic type, to this value.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.TYPE_USE})
public @interface Modifiable {
}
