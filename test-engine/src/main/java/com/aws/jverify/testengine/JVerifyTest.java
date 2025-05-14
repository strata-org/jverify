package com.aws.jverify.testengine;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface JVerifyTest {
    boolean verifyByDefault() default true;
    int exitCode() default 0;
    int dafnyVerified() default -1;
    int dafnyErrors() default -1;
}
