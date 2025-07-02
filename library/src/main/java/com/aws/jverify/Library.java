package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target; 

/**
 * Specify whether this package is a library or not
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE})
public @interface Library {}
