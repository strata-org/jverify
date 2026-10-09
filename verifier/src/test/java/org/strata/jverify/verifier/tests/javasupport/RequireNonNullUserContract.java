// ^ /object-contract.java(162:1-162:2) error: class 'Objects' must not be referenced by more than one @Contract annotation
package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import java.util.Objects;

/**
 * The injected object-contract.java already declares the contract for
 * {@code java.util.Objects}, so a user's own {@code @Contract(Objects.class)}
 * is a duplicate.
 */
// The program's own @Contract(Objects.class) silently takes precedence over the
// injected ObjectsContract, so the duplicate is not reported.
@JVerifyTest(expectedToFail = true, blockedBy = Blocker.FRONTEND_OTHER, exitCode = 2)
class RequireNonNullUserContract {
    static Object userCall(Object b) {
        return Objects.requireNonNull(b);
    }
}

@Contract(Objects.class)
class UserObjectsContract {
    public static <T> T requireNonNull(T obj) {
        throw new ContractException();
    }
}
