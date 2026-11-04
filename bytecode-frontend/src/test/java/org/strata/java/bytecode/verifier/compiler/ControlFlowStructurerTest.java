package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.strata.jverify.laurel.AstNode;
import org.strata.jverify.laurel.GotoDestination;
import org.strata.jverify.laurel.StmtExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The back-edge placement on synthetic trees: every falling-through tail of
 * a redirected loop body must end in the back edge, and a tail the
 * distribution misses must refuse the method rather than silently lose the
 * edge (control would fall out of the loop where the program iterates --
 * the unsound miss class of the labelled-block switch arm).
 */
class ControlFlowStructurerTest {

    private static AstNode<StmtExpr> node(StmtExpr v) {
        return new AstNode<>(v, SourceMap.UNKNOWN_RANGE);
    }

    private static AstNode<StmtExpr> block(Optional<String> label, List<AstNode<StmtExpr>> stmts) {
        return node(new StmtExpr.Block(new ArrayList<>(stmts), label, List.of(), Optional.empty()));
    }

    private static AstNode<StmtExpr> backEdge(String label) {
        return node(new StmtExpr.Goto(label, new GotoDestination.BlockStart()));
    }

    private static void assertEndsInBackEdge(AstNode<StmtExpr> armNode, String label) {
        StmtExpr.Block arm = assertInstanceOf(StmtExpr.Block.class, armNode.val());
        StmtExpr last = arm.statements().get(arm.statements().size() - 1).val();
        StmtExpr.Goto g = assertInstanceOf(StmtExpr.Goto.class, last);
        assertEquals(label, g.target());
        assertInstanceOf(GotoDestination.BlockStart.class, g.destination());
    }

    @Test
    void backEdgeIsPlacedAfterALabelledBlockArm() {
        // An if/else whose then-arm is a bare labelled block (a nested loop,
        // as a switch emitter's unwrapped single-statement arm) and whose
        // else-arm is a bare statement: both fall through, so both must end
        // in the back edge after distribution.
        var nested = block(Optional.of("inner"), List.of());
        var elseArm = node(new StmtExpr.LiteralInt(1));
        var ite = node(new StmtExpr.IfThenElse(node(new StmtExpr.LiteralBool(true)),
                nested, Optional.of(elseArm)));
        var out = new ArrayList<>(List.of(ite));

        ControlFlowStructurer.placeBackEdge(out, backEdge("outer"));

        assertEquals(1, out.size(), "distributed in place, nothing appended");
        var placed = assertInstanceOf(StmtExpr.IfThenElse.class, out.get(0).val());
        assertEndsInBackEdge(placed.thenBranch(), "outer");
        assertEndsInBackEdge(placed.elseBranch().orElseThrow(), "outer");
        assertFalse(ControlFlowStructurer.fallsThroughStmt(out.get(0)),
                "no falling tail may remain");
    }

    @Test
    void anArmThatCannotFallGetsNoBackEdge() {
        // The then-arm already leaves (a goto elsewhere): only the falling
        // else-arm gets the edge.
        var leaves = block(Optional.empty(),
                List.of(node(new StmtExpr.Goto("elsewhere", new GotoDestination.BlockEnd()))));
        var falling = node(new StmtExpr.LiteralInt(1));
        var ite = node(new StmtExpr.IfThenElse(node(new StmtExpr.LiteralBool(true)),
                leaves, Optional.of(falling)));
        var out = new ArrayList<>(List.of(ite));

        ControlFlowStructurer.placeBackEdge(out, backEdge("outer"));

        var placed = assertInstanceOf(StmtExpr.IfThenElse.class, out.get(0).val());
        assertEquals(leaves, placed.thenBranch(), "the leaving arm is untouched");
        assertEndsInBackEdge(placed.elseBranch().orElseThrow(), "outer");
    }

    @Test
    void backEdgeIsAppendedAfterALabelledBlockEndingAnUnlabelledBlock() {
        // A nested loop as the LAST statement of a plain block: the edge is
        // appended right after it, inside the block.
        var inner = block(Optional.of("inner"), List.of());
        var outerBlk = block(Optional.empty(), List.of(node(new StmtExpr.LiteralInt(1)), inner));
        var out = new ArrayList<>(List.of(outerBlk));

        ControlFlowStructurer.placeBackEdge(out, backEdge("outer"));

        assertEquals(1, out.size());
        var placed = assertInstanceOf(StmtExpr.Block.class, out.get(0).val());
        assertEquals(3, placed.statements().size());
        assertEndsInBackEdge(out.get(0), "outer");
        assertFalse(ControlFlowStructurer.fallsThroughStmt(out.get(0)));
    }

    @Test
    void aMissedFallingTailRefuses() {
        // The fail-closed gate: a distribution result with a falling arm the
        // distributor did not cover -- here the pre-fix finding shape, a bare
        // labelled block arm with no jump while its sibling got one -- must
        // refuse the method, never emit the loop. This is what makes any
        // still-unenumerated tail shape safe: it can only refuse, not verify
        // a false invariant.
        var missed = block(Optional.of("inner"), List.of());    // falls through, no back edge
        var jumped = block(Optional.empty(),
                List.of(node(new StmtExpr.LiteralInt(1)), backEdge("outer")));
        var ite = node(new StmtExpr.IfThenElse(node(new StmtExpr.LiteralBool(true)),
                missed, Optional.of(jumped)));

        assertTrue(ControlFlowStructurer.fallsThroughStmt(ite));
        assertThrows(JavaViolationException.class,
                () -> ControlFlowStructurer.requireBackEdgeOnEveryPath(ite));
    }
}
