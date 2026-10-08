package org.strata.java.bytecode.verifier.laurel;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.Body;
import org.strata.jverify.laurel.CoroutineContracts;
import org.strata.jverify.laurel.FileRange;
import org.strata.jverify.laurel.Identifier;
import org.strata.jverify.laurel.Procedure;
import org.strata.jverify.laurel.Program;
import org.strata.jverify.laurel.Raw;
import org.strata.jverify.laurel.SourceRange;
import org.strata.jverify.laurel.StmtExpr;
import org.strata.jverify.laurel.ToIon;
import org.strata.jverify.laurel.Uri;
import org.strata.jverify.verifier.IntervalTree;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.java.bytecode.verifier.laurel.PositionResolver;
import org.strata.java.bytecode.verifier.MethodVerificationStatus;
import org.strata.java.bytecode.verifier.VerifierOptions;

import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test of the ported Laurel layer: hand-construct a tiny
 * Laurel program, serialize it to binary Ion, run `strataLaurel
 * laurelAnalyzeBinary --solver z3` (on PATH via the Strata build-tool),
 * and check the parsed verdict. Proves the Laurel AST + Ion serialization +
 * process protocol survived the port before any frontend exists.
 */
class StrataBackendSmokeTest {

    private static final URI FAKE_FILE = Path.of("/tmp/Smoke.java").toUri();

    private static FileRange range(int start, int stop) {
        return new FileRange(new Uri(FAKE_FILE.toString()), new SourceRange(new Raw(start), new Raw(stop)));
    }

    private static Identifier ident(String name) {
        return new Identifier(name, Optional.empty(), range(0, 0));
    }

    private static AstNode<StmtExpr> node(StmtExpr e, int start, int stop) {
        return new AstNode<>(e, range(start, stop));
    }

    private static Procedure procedure(String name, StmtExpr.Block block) {
        return new Procedure(
                ident(name),
                List.of(),                                     // typeArgs
                List.of(),                                     // inputs
                List.of(),                                     // outputs
                List.of(),                                     // preconditions
                new CoroutineContracts.Regular(),              // contracts
                Optional.empty(),                              // decreases
                new Body.Opaque(List.of(), Optional.of(node(block, 0, 0)),
                        List.of(new org.strata.jverify.laurel.ModifiesGroup(
                                List.of(), Optional.empty(), Optional.empty()))),
                Optional.empty(),                              // invokeOn
                false,                                         // isInterpretEntry
                List.of(),                                     // axioms
                Optional.empty(),                              // throwsType
                Optional.empty(),                              // throwsBinding
                List.of(),                                     // throwsOn
                List.of(),                                     // readsGlobals
                List.of());                                    // writesGlobals
    }

    /** Resolver over a pretend 10-column-per-line file, matching JFE's offset->line:col logic. */
    private static final PositionResolver TEN_COL_LINES = (uri, offset) ->
            new Position((int) (offset / 10) + 1, (int) (offset % 10) + 1);

    private static VerifierOptions options() {
        return new VerifierOptions(
                new PrintWriter(System.out, true),
                Path.of(System.getProperty("user.dir")),
                List.of(),
                null,
                false,
                true,
                List.of(),
                false,
                true,
                false,
                false,
                false,
                false,
                null,
                null,
                false);
    }

    private static Map<URI, IntervalTree<Integer, MethodVerificationStatus>> statuses(String methodName) {
        var tree = new IntervalTree<Integer, MethodVerificationStatus>();
        // The whole fake method spans lines 1..5.
        tree.insert(1, 5, new MethodVerificationStatus(methodName,
                new org.strata.jverify.common.Range(new Position(1, 1), new Position(5, 1)),
                MethodVerificationStatus.VerificationStatus.Verified));
        var map = new HashMap<URI, IntervalTree<Integer, MethodVerificationStatus>>();
        map.put(FAKE_FILE, tree);
        return map;
    }

    @Test
    void assertTrueVerifies() {
        var block = new StmtExpr.Block(List.of(
                node(new StmtExpr.Assert(node(new StmtExpr.LiteralBool(true), 12, 16), Optional.empty()), 10, 18)
        ), Optional.empty());
        var program = new Program(List.of(procedure("Smoke_ok", block)), List.of(), List.of(), List.of());

        var results = new StrataBackend(options()).verifyProgram(program, TEN_COL_LINES, statuses("Smoke_ok"));

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results,
                () -> "expected a completed run, got " + results);
        assertEquals(0, ran.exitCode(), "exit code");
        assertEquals(List.of(), ran.diagnostics(), "diagnostics");
        assertEquals(1, ran.verificationResults().verificationPassedMethods(), "verified methods");
        assertEquals(0, ran.verificationResults().verificationFailedAssertions(), "failed assertions");
    }

    @Test
    void assertFalseFails() {
        var block = new StmtExpr.Block(List.of(
                node(new StmtExpr.Assert(node(new StmtExpr.LiteralBool(false), 12, 17), Optional.empty()), 10, 19)
        ), Optional.empty());
        var program = new Program(List.of(procedure("Smoke_bad", block)), List.of(), List.of(), List.of());

        var results = new StrataBackend(options()).verifyProgram(program, TEN_COL_LINES, statuses("Smoke_bad"));

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results,
                () -> "expected a completed run, got " + results);
        assertEquals(4, ran.exitCode(), "exit code (4 is synthesized from diagnostics)");
        assertEquals(1, ran.diagnostics().size(), "diagnostic count");
        assertTrue(ran.diagnostics().getFirst().getMessage(null).toLowerCase().contains("assert"),
                () -> "diagnostic should mention the assertion: " + ran.diagnostics().getFirst().getMessage(null));
        assertEquals(1, ran.verificationResults().verificationFailedMethods(), "failed methods");
        assertEquals(1, ran.verificationResults().verificationFailedAssertions(), "failed assertions");
    }

    /** The generated AST must round-trip to Ion without needing a live backend. */
    @Test
    void serializesToIon() {
        var block = new StmtExpr.Block(List.of(
                node(new StmtExpr.Assert(node(new StmtExpr.LiteralBool(true), 0, 0), Optional.empty()), 0, 0)
        ), Optional.empty());
        var program = new Program(List.of(procedure("Smoke_ion", block)), List.of(), List.of(), List.of());
        var ion = com.amazon.ion.system.IonSystemBuilder.standard().build();
        var value = program.toIon(ion);
        assertTrue(value.toString().contains("Smoke_ion"), "program name should appear in the Ion s-exp");
    }
}
