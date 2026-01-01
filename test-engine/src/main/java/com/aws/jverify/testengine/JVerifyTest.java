package com.aws.jverify.testengine;

import com.aws.jverify.generated.DefaultClassDecl;
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
 *          - Verification should finish with exit code {@code X} without Dafny terminating normally
 *          (i.e. Dafny is never invoked because there are javac errors, or Dafny terminates abnormally).
 *      </li>
 *      <li>
 *          {@code @JVerifyTest(exitCode = ..., dafnyVerified = ..., dafnyErrors = ...)}
 *          - Verification should finish with exit code {@code X}, Dafny terminates normally,
 *          and Dafny's summary reports {@code Y} verified symbols and {@code Z} errors.
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

    int dafnyErrors() default -1;

    Backend[] BACKENDS() default { Backend.Dafny };

    int[] performanceTicks() default { };
    
    String[] additionalFiles() default {};
    
    boolean verifyPrintedDafny() default false;

    /**
     * Expected number of Java methods to fail verification
     */
    int javaErrors()  default -1;

    /**
     * Expected number of Java methods to be verified
     * Note: add + 1 for each class you are verifying to account
     * for the implicit constructor unless you have specified it explicitly
     */
    int javaVerified() default -1;

    /**
     * Expected number of Java methods to have been skipped during verification
     */
    int javaSkipped() default -1;
}