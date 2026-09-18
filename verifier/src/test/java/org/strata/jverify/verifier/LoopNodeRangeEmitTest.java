package org.strata.jverify.verifier;

import com.amazon.ion.IonContainer;
import com.amazon.ion.IonList;
import com.amazon.ion.IonSexp;
import com.amazon.ion.IonStruct;
import com.amazon.ion.IonSymbol;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
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
 * Regression test: every node the {@code for}-loop lowering synthesizes carries a
 * source range.
 *
 * <p>A loop's implicit "invariant holds on entry" obligation has no Java expression of
 * its own, so it lands on a synthesized node. {@code emitLoop} built those with the
 * range-less {@code node(expr)} overload; for a loop with no invariant the diagnostic
 * came out at {@code <unknown>(1:1)}, unattributable to a method, and was DROPPED from
 * the per-method counts. No count assertion could catch it; the only signal was a stderr
 * warning.
 *
 * <p>Bisected to the {@code for}-loop STEP node -- a {@code while} has no step and does
 * not regress, hence unnoticed. Asserting over the whole loop subtree rather than that
 * one node keeps the test honest if the lowering changes shape.
 *
 * <p>{@code -Djverify.strictSourceRanges=true} (set for all verifier tests) hard-fails a
 * range-less diagnostic, but only one that fires. This test pins the emitted ranges
 * directly, catching a regression even where no obligation fails.
 */
public class LoopNodeRangeEmitTest {

    @Test
    public void forLoopSynthesizedNodesCarrySourceRanges() throws Exception {
        String source = """
                import static org.strata.jverify.JVerify.*;
                class Counting {
                    static void count() {
                        int i = 0;
                        for (i = 0; i < 5; i = i + 1) {
                        }
                        check(i == 5);
                    }
                }
                """;
        var sourceFile = new SourceFile(Path.of("Counting.java"), source);

        Path emittedLaurel = Files.createTempFile("emitted-laurel-loop-range", ".ion");
        Files.deleteIfExists(emittedLaurel);
        try {
            var baseOptions = JVerifyTestEngine.getVerifierOptions(
                    JVerifyTestEngine.makeJVerifyTestAnnotation(1, 0), null);
            var emitOptions = EmitLaurelWorkflowTest.withEmit(
                    baseOptions, emittedLaurel, /* emitLaurelOnly */ true);
            var emitResult = Driver.getDriver(emitOptions)
                    .verifyJavaFiles(new java.util.ArrayList<>(List.of(sourceFile)));
            assertEquals(0, emitResult.exitCode(), "the emit-only run should succeed");

            IonSystem ion = IonSystemBuilder.standard().build();
            IonValue emittedProgram = ion.singleValue(Files.readAllBytes(emittedLaurel));

            IonSexp whileOp = findOp(emittedProgram, "While");
            assertNotNull(whileOp, "the emitted program should contain a While node");
            // (While <cond> <invariants[]> <decreases|null> <body>) -- positional.
            assertEquals(6, whileOp.size(),
                    "unexpected While arity; this test walks the op by position");

            // Target the STEP node. A `for`'s lowered body is `{ <user body>, <step> }`;
            // the step regressed -- it is synthesized (no Java statement owns the
            // increment as a loop-body statement), so the implicit "invariant holds on
            // entry" obligation lands on it.
            //
            // Identify it STRUCTURALLY, not by searching the subtree for the `for`
            // token's offset: 12 other nodes in this loop carry that same range, so a
            // presence check passes with the bug reintroduced (measured: 14 nodes at that
            // offset when fixed, 12 when broken).
            IonStruct body = (IonStruct) whileOp.get(4);
            assertTrue(isAstNode(body), "the While body should be a serialized AstNode");
            IonSexp loweredBlock = asOp(body.get("val"), "Block");
            assertNotNull(loweredBlock, "a for-loop's body lowers to a Block");
            IonList blockStmts = (IonList) loweredBlock.get(1);
            assertNotNull(blockStmts, "the lowered Block should carry its statements");
            assertEquals(2, blockStmts.size(),
                    "a for-loop body lowers to exactly { <user body>, <step> }");

            IonStruct step = (IonStruct) blockStmts.get(1);
            int forToken = source.indexOf("for (i = 0;");
            assertTrue(forToken > 0, "the probe source should contain the for loop");
            assertEquals(forToken, startByte(step),
                    "the synthesized loop-step node must carry the `for` statement's "
                    + "range. Without it the implicit \"invariant holds on entry\" "
                    + "obligation is reported at <unknown>(1:1) and dropped from the "
                    + "per-method counts, so no count assertion catches the regression.");
        } finally {
            Files.deleteIfExists(emittedLaurel);
        }
    }

    /** DFS for the first {@code (OpName ...)} s-expression. */
    private static IonSexp findOp(IonValue value, String opName) {
        if (value instanceof IonSexp sexp && !sexp.isEmpty()
                && sexp.get(0) instanceof IonSymbol sym
                && opName.equals(sym.stringValue())) {
            return sexp;
        }
        if (value instanceof IonContainer container) {
            for (IonValue child : container) {
                IonSexp found = findOp(child, opName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** {@code value} as an {@code (opName ...)} s-expression, or null if the head differs. */
    private static IonSexp asOp(IonValue value, String opName) {
        return value instanceof IonSexp sexp && !sexp.isEmpty()
                && sexp.get(0) instanceof IonSymbol sym
                && opName.equals(sym.stringValue()) ? sexp : null;
    }

    /**
     * This node's start byte, or -1 when it carries no real location. The range-less
     * sentinel is NOT a null source: it is a populated struct whose file is the literal
     * {@code "<unknown>"} and whose range is 0-0, so a null check passes vacuously.
     */
    private static int startByte(IonStruct node) {
        if (!(node.get("source") instanceof IonStruct source)
                || !(source.get("file") instanceof IonStruct file)
                || !(file.get("_0") instanceof com.amazon.ion.IonText uri)
                || uri.stringValue().endsWith("<unknown>")) {
            return -1;
        }
        return source.get("range") instanceof IonStruct range
                && range.get("start") instanceof IonStruct start
                && start.get("byteIdx") instanceof com.amazon.ion.IonInt idx
                ? idx.intValue() : -1;
    }

    private static boolean isAstNode(IonValue value) {
        return value instanceof IonStruct struct
                && struct.containsKey("val") && struct.containsKey("source");
    }
}
