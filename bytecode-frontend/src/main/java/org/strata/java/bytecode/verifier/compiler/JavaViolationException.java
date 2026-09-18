package org.strata.java.bytecode.verifier.compiler;

/**
 * An input program uses a Java feature (or a JVerify spec form) this
 * frontend cannot translate to Laurel. Reported as a per-method diagnostic;
 * the method is marked skipped. Port of JFE's JavaViolationException.
 */
public class JavaViolationException extends RuntimeException {
    public JavaViolationException(String message) {
        super(message);
    }
}
