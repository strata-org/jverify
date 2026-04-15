package com.aws.jverify.testengine;

import com.aws.jverify.verifier.Backend;
import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valid annotation values:
 *
 * <ol>
 *      <li>
 *          {@code @VerifyTest(skip = "...")}
 *          - The test should be skipped for the given reason.
 *      </li>
 *      <li>
 *          {@code @VerifyTest(exitCode = ...)}
 *          - JVerify should finish with exit code {@code X}
 *          (i.e. Verification is never started because there are javac errors, or verification terminates abnormally).
 *      </li>
 *      <li>
 *          {@code @JVerifyTest(methodsVerified = ..., errorCount = ...)}
 *          - {@code methodsVerified} are verified successfully, and there are {@code errorCount} verification errors.
 *      </li>
 * </ol>
 *
 * Other parameters such as {@code verifyByDefault} can be set independently.
 */
@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface JVerifyTest {
    /**
     * Mark the test as skipped for the given reason (if not null or empty).
     */
    String skip() default "";

    /**
     * What to pass for the --verify-by-default verifier option.
     */
    boolean verifyByDefault() default true;

    boolean useBuiltinContracts() default false;
    
    boolean continueOnErrors() default false;

    int exitCode() default 0;

    Backend[] BACKENDS() default { Backend.Strata };

    int[] performanceTicks() default { };
    
    String[] additionalFiles() default {};
    
    boolean verifyPrintedDafny() default false;

    /**
     * Expected number of Java methods to fail verification
     */
    int methodsInvalid()  default -1;
    
    int errorCount()  default -1;

    /**
     * Expected number of Java methods to be verified
     * Note: add + 1 for each class you are verifying to account
     * for the implicit constructor unless you have specified it explicitly
     */
    int methodsVerified() default -1;

    /**
     * Expected number of Java methods to have been skipped during verification
     */
    int methodsSkipped() default -1;
}