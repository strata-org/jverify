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
    String skip() default "";
    boolean verifyByDefault() default true;
    boolean useBuiltinContracts() default false;
    boolean continueOnErrors() default false;
    int exitCode() default 0;
    int[] performanceTicks() default { };
    String[] additionalFiles() default {};
    int methodsInvalid()  default -1;
    int errorCount()  default -1;
    int methodsVerified() default -1;
    int methodsSkipped() default -1;
}
