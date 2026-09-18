package org.strata.jverify.verifier;

import com.amazon.ion.IonSexp;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTestEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: the emitted Laurel Ion carries a real source range on a
 * method's return type — both on the outer {@code Laurel.returnType} op and on
 * the inner type op it wraps.
 *
 * <p>The inner type op is translated from a resolved javac
 * {@link com.sun.tools.javac.code.Type}, which has no tree position; the
 * compiler must stamp the declared type tree's range ({@code method.restype})
 * onto it. Without that, the inner op serializes a null range, Strata reads it
 * back as its "no location" sentinel, and an implicit no-overflow failure on
 * the result (e.g. {@code -x} overflowing {@code int32} in {@code abs}) gets
 * reported at a whole-file fallback position instead of at the return type.
 *
 * <p>Serialization convention (see {@code IonSerializer.newOp}): each op is an
 * s-expression {@code (opName range …args)} where {@code range} is either a
 * two-int s-expression {@code (start stop)} or null for "no location".
 */
public class ReturnTypeRangeEmitTest {

    @Test
    public void returnTypeInnerOpCarriesDeclaredTypeRange() throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                class Abs {
                    static int abs(int x) {
                        return x < 0 ? -x : x;
                    }
                }
                """;
        var sourceFile = new SourceFile(Path.of("Abs.java"), source);

        Path emittedLaurel = Files.createTempFile("emitted-laurel-range", ".ion");
        Files.deleteIfExists(emittedLaurel);
        try {
            var baseOptions = JVerifyTestEngine.getVerifierOptions(
                    JVerifyTestEngine.makeJVerifyTestAnnotation(1, 0), null);
            var emitOptions = EmitLaurelWorkflowTest.withEmit(
                    baseOptions, emittedLaurel, /* emitLaurelOnly */ true);
            var emitDriver = Driver.getDriver(emitOptions);
            var emitResult = emitDriver
                    .verifyJavaFiles(new java.util.ArrayList<>(List.of(sourceFile)));
            assertEquals(0, emitResult.exitCode(), "the emit-only run should succeed");

            IonSystem ion = IonSystemBuilder.standard().build();
            IonValue emittedProgram = ion.singleValue(Files.readAllBytes(emittedLaurel));

            IonSexp returnTypeOp = findOp(emittedProgram, "Laurel.returnType");
            assertNotNull(returnTypeOp, "the emitted program should contain a Laurel.returnType op");

            int[] outerRange = opRange(returnTypeOp);
            assertNotNull(outerRange, "the returnType op itself should carry a source range");

            IonSexp innerTypeOp = firstNestedOp(returnTypeOp);
            assertNotNull(innerTypeOp, "the returnType op should wrap an inner type op");
            int[] innerRange = opRange(innerTypeOp);
            assertNotNull(innerRange,
                    "the inner type op must carry the declared type tree's range, not a null range");

            // Both ranges denote the `int` return-type token of `static int abs`.
            int intTokenStart = source.indexOf("static int abs") + "static ".length();
            assertEquals(intTokenStart, innerRange[0],
                    "the inner type range should start at the `int` return-type token");
            assertEquals(intTokenStart + "int".length(), innerRange[1],
                    "the inner type range should stop after the `int` return-type token");
            assertEquals(outerRange[0], innerRange[0], "outer and inner ranges should agree");
            assertEquals(outerRange[1], innerRange[1], "outer and inner ranges should agree");

            // Parameter types get the same treatment: `int x`'s type op has a range too.
            IonSexp parameterOp = findOp(emittedProgram, "Laurel.parameter");
            assertNotNull(parameterOp, "the emitted program should contain a Laurel.parameter op");
            IonSexp paramTypeOp = firstNestedOp(parameterOp);
            assertNotNull(paramTypeOp, "the parameter op should wrap an inner type op");
            assertNotNull(opRange(paramTypeOp),
                    "the parameter's inner type op must carry the declared type tree's range");
        } finally {
            Files.deleteIfExists(emittedLaurel);
        }
    }

    /** Depth-first search for the first op s-expression whose head symbol is {@code opName}. */
    private static IonSexp findOp(IonValue value, String opName) {
        if (value instanceof IonSexp sexp) {
            if (!sexp.isEmpty() && sexp.get(0) instanceof IonSymbol sym
                    && opName.equals(sym.stringValue())) {
                return sexp;
            }
        }
        if (value instanceof com.amazon.ion.IonContainer container) {
            for (IonValue child : container) {
                IonSexp found = findOp(child, opName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * The source range of an op s-expression {@code (opName range …)}: a two-element
     * {@code int[]} for a real {@code (start stop)} range, or {@code null} for the
     * null "no location" encoding.
     */
    private static int[] opRange(IonSexp op) {
        if (op.size() < 2 || op.get(1).isNullValue()) {
            return null;
        }
        IonSexp range = (IonSexp) op.get(1);
        return new int[] {
                ((com.amazon.ion.IonInt) range.get(0)).intValue(),
                ((com.amazon.ion.IonInt) range.get(1)).intValue()
        };
    }

    /** The first op s-expression nested (at any depth) inside {@code op}'s arguments. */
    private static IonSexp firstNestedOp(IonSexp op) {
        for (int i = 1; i < op.size(); i++) {
            IonSexp found = anyOp(op.get(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static IonSexp anyOp(IonValue value) {
        if (value instanceof IonSexp sexp && !sexp.isEmpty()
                && sexp.get(0) instanceof IonSymbol sym
                && sym.stringValue().startsWith("Laurel.")) {
            return sexp;
        }
        if (value instanceof com.amazon.ion.IonContainer container) {
            for (IonValue child : container) {
                IonSexp found = anyOp(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
