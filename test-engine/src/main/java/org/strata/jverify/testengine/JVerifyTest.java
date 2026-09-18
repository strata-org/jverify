package org.strata.jverify.testengine;

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
 *          {@code @JVerifyTest(skip = "...")}
 *          - The test should be skipped for the given reason.
 *      </li>
 *      <li>
 *          {@code @JVerifyTest(exitCode = ...)}
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
    String[] additionalFiles() default {};
    /**
     * Expected number of Java methods to fail verification.
     */
    int methodsInvalid()  default -1;
    int errorCount()  default -1;
    /**
     * Expected number of Java methods to be verified.
     * Note: add + 1 for each class you are verifying to account
     * for the implicit constructor unless you have specified it explicitly.
     */
    int methodsVerified() default -1;
    /**
     * Expected number of Java methods to have been skipped during verification.
     */
    int methodsSkipped() default -1;

    /**
     * Mark this test as an EXPECTED FAILURE (xfail): the feature under test is
     * known-broken today. The engine still RUNS it, but inverts the outcome:
     *  - if it still fails as expected -> reported as skipped (build stays green);
     *  - if it now unexpectedly PASSES (all expectations met) -> the build FAILS
     *    (XPASS), signalling that the marker should be removed.
     * This replaces the old {@code skip="..."} short-circuit, so a feature that
     * starts working can never stay silently skipped. Every xfail must list its
     * {@link #blockedBy()}.
     */
    boolean expectedToFail() default false;

    /**
     * The blocker(s) that make this an {@link #expectedToFail()} test. Must be
     * non-empty when {@code expectedToFail} is true. A test lists ALL of its
     * blockers; it is only expected to start passing once every one has cleared.
     */
    Blocker[] blockedBy() default {};

    /**
     * Run the program in --interpret mode (concretely execute {@link #entry()})
     * instead of verifying it. See {@code VerifierOptions.interpret}.
     */
    boolean interpret() default false;
    /**
     * The entry point to execute in {@link #interpret()} mode, in
     * {@code pkg.Class#method} form. Ignored unless {@code interpret} is true.
     */
    String entry() default "";
    /**
     * Execution-step budget (fuel) for {@link #interpret()} mode. {@code <= 0}
     * leaves the interpreter default in place. Ignored unless interpreting.
     */
    int fuel() default -1;
}
