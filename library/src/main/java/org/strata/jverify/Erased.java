package org.strata.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
Enables the use of these constructs: 
- @Unbounded
- forall, exists

But prevents the method from being executed at runtime.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Erased {
}
