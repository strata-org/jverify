package org.strata.jverify;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
This method's contract should be used to generate a jqwik property
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface AsProperty {
}

