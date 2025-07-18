package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Let the definitions in the class this annotation is attached to,
 * serve as a contract for the class this annotation references.
 * 
 * A class may only be referenced by a @Contract annotation once.
 * 
 * If the referenced class occur in source, 
 * @Verify annotations must be so that the class is not verified
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Contract {
    Class<?> value() default Contract.class;
    
    /*
    Can only be used for contracts on classes that are defined in libraries
    
    Allows constructors for this type to be used in pure code.
    Prevents using the `==` operator on values of this type
    
    Must only be added if this class is immutable, 
    and if its equality has hashcode methods implement structural equality
     */
    boolean immutable() default false;
}