package org.strata.java.bytecode.frontend;

/**
 * An input {@code .class} file is not structurally readable as a classfile
 * (truncated, corrupted, or not a classfile at all), so no {@code ClassModel}
 * exists to hand to translation. This is the structural sibling of the
 * content-level JVM bytecode-verification gate in the compiler, which this
 * failure never reaches (verification needs a parsed model). Callers surface
 * it as an input refusal (exit 2 at the driver), never a crash.
 *
 * <p>The message names the offending file and the parser's reason; the
 * parser's original failure rides along as the {@link Throwable#getCause()
 * cause}.
 */
public final class UnparseableClassfileException extends RuntimeException {

    public UnparseableClassfileException(String message, Throwable cause) {
        super(message, cause);
    }
}
