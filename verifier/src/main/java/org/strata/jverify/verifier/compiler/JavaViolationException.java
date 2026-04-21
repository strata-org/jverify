package org.strata.jverify.verifier.compiler;

/**
 * Occurs when the contracts that we expect from Java resolution are violated
 * Indicates a JVerify compiler bug
 */
public class JavaViolationException extends RuntimeException {
    public JavaViolationException() {
        super();
    }
    
    public JavaViolationException(String message) {
        super(message);
    }
}
