package org.strata.jverify.laurel;

import com.amazon.ion.IonStruct;
import com.amazon.ion.system.IonSystemBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModifiesGroup#toIon} field-by-field. JVerify itself only ever emits
 * the degenerate unguarded group, so the guard/summary PRESENT branches are
 * never exercised end to end — but the serializer is generated against
 * Strata's deserializer contract (by-name struct fields, null for none), and
 * a drift in the present-branches would only surface when a pass-produced
 * program is re-serialized. Pinned here directly instead.
 */
class ModifiesGroupIonTest {

    private static final com.amazon.ion.IonSystem ION = IonSystemBuilder.standard().build();

    private static final FileRange UNKNOWN_SOURCE =
            new FileRange(new Uri("<unknown>"), new SourceRange(new Raw(0), new Raw(0)));

    private static AstNode<StmtExpr> node(StmtExpr e) {
        return new AstNode<>(e, UNKNOWN_SOURCE);
    }

    @Test
    void unguardedGroupSerializesNullGuardAndSummary() {
        var group = new ModifiesGroup(List.of(node(new StmtExpr.All())),
                Optional.empty(), Optional.empty());
        IonStruct s = (IonStruct) group.toIon(ION);
        assertEquals(1, ((com.amazon.ion.IonList) s.get("targets")).size());
        assertTrue(s.get("guard").isNullValue());
        assertTrue(s.get("summary").isNullValue());
    }

    @Test
    void presentGuardAndSummarySerializeByName() {
        var guard = node(new StmtExpr.LiteralBool(true));
        var group = new ModifiesGroup(List.of(node(new StmtExpr.All())),
                Optional.of(guard), Optional.of("throwsOn modifies clause"));
        IonStruct s = (IonStruct) group.toIon(ION);
        assertTrue(!s.get("guard").isNullValue(), "present guard must not serialize as null");
        assertEquals("throwsOn modifies clause",
                ((com.amazon.ion.IonString) s.get("summary")).stringValue());
        // The guard is an AstNode payload; its serialized form is the sexp of
        // the wrapped expression, matching Strata's by-name reader.
        assertEquals(3, s.size());
    }
}
