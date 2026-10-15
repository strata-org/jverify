package org.strata.java.bytecode.frontend;

import java.io.IOException;

/**
 * A cycle in the superclass chain of a class a compilation mentions ({@link TypeRecorder}). Callers surface it
 * as an input refusal, not a crash. It is an {@link IOException} so that a contract source's cycle is reported
 * like the contract compile's other I/O failures.
 */
public final class SuperclassCycleException extends IOException {

    SuperclassCycleException(String className) {
        super("cyclic inheritance involving " + className);
    }
}
