package com.aws.jverify.testengine;

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
     * What to pass for the --verify-by-default verifier option.
     */
    boolean verifyByDefault() default true;

    int exitCode() default 0;

    int dafnyVerified() default -1;

    int dafnyErrors() default -1;
}
