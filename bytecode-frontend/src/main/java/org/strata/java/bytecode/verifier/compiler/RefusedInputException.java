package org.strata.java.bytecode.verifier.compiler;

/**
 * The input cannot be verified at all (e.g. it carries no debug
 * information), and {@link BytecodeToLaurelCompiler#analyze} refuses to
 * produce a program for it. The user-facing reason is already reported on
 * the {@link Reporter}. Thrown -- rather than returning an empty result --
 * so a caller that ignores the reporter cannot mistake the refusal for a
 * clean zero-findings verification.
 */
public final class RefusedInputException extends RuntimeException {

    public RefusedInputException(String message) {
        super(message);
    }
}
