package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.JVerify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.throwsOn;

/**
 * Translator-level refusals of the exceptional-contract DSL
 * (docs/exceptions-design.md §3): a case needs a declared {@code throws}
 * clause to hang on, a case postcondition's parameter must be typed at the
 * declared throws type (single or joined), and a case must be one chained
 * statement (captured/detached handles are refused, not misfiled). All
 * reject per method, leaving siblings verifiable (the same skip machinery
 * TranslatorSkip pins). A {@code throwsOn} in a LOOP contract is rejected
 * earlier and file-wide, by {@code MethodOrLoopContractCompiler.visitLoop}'s
 * wrongContract check -- pinned in TranslationErrors beside its siblings.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 5, // fine() + 4 implicit constructors
        methodsSkipped = 5,
        methodsInvalid = 0, // also arms the errorCount expectation below
        errorCount = 0
)
class ThrowsOnSkips {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
    static class ParseError extends AppException {}
    static class IoError extends AppException {}

    /** A detached handle a clause could be called on (see detachedClause). */
    static final JVerify.ThrowsOnCase DETACHED = null;

    /** A case without a declared throws clause. */
    static int caseWithoutThrows(int x) {
//             ^ error: throwsOn requires the method to declare the thrown type in its throws clause
        throwsOn(x < 0);
        return x;
    }

    /** A case postcondition's parameter typed below the declared throws type. */
    static int narrowPostconditionParam(int x) throws AppException {
//             ^ error: a throwsOn case's postcondition parameter must be typed at the method's declared throws type ('org.strata.jverify.verifier.tests.javasupport.exceptions.ThrowsOnSkips.AppException'); narrow with instanceof and a cast inside the property
        throwsOn(x < 0).postcondition((ParseError e) -> true);
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** Under a multi-throws join the parameter must be the JOIN type
        ({@code AppException}); a declared member below the join is still
        refused (ThrowsOnContract pins the accept side). */
    static int narrowUnderJoin(int x) throws ParseError, IoError {
//             ^ error: a throwsOn case's postcondition parameter must be typed at the method's declared throws type ('org.strata.jverify.verifier.tests.javasupport.exceptions.ThrowsOnSkips.AppException'); narrow with instanceof and a cast inside the property
        throwsOn(x < 0).postcondition((ParseError e) -> true);
        if (x < 0) {
            throw new ParseError();
        }
        return x;
    }

    /** Capturing the ThrowsOnCase handle: the declaration ends the contract
        block, so the throwsOn call converts as a body expression and is
        refused there -- the clauses below it are never silently misfiled as
        normal-exit clauses of the same name. */
    static int capturedHandle(int x) throws AppException {
//             ^ error: Unsupported JVerify method: throwsOn
        JVerify.ThrowsOnCase c = throwsOn(x < 0);
        c.postcondition((AppException e) -> true);
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }

    /** A case clause on a handle that never chained off throwsOn in this
        contract: refused rather than misread as the flat clause. */
    static int detachedClause(int x) throws AppException {
//             ^ error: a throwsOn case clause must chain directly off throwsOn(...); calling 'postcondition' on a captured handle detaches it from its case
        DETACHED.postcondition((AppException e) -> true);
        return x;
    }

    /** Siblings stay verifiable. */
    static int fine(int x) {
        return x;
    }
}
