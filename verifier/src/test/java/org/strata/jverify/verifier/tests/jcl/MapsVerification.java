package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.Impure;
import org.strata.jverify.JVerify;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

/**
 * What the generic {@code JVerify.Map<K, V>} buys: a PARTIAL map over reference types whose reads
 * are stable.
 *
 * <p>{@code Map<K, V>} lowers to Laurel's partial map, {@code get} to {@code mapGet} and
 * {@code contains} to {@code mapContains}. Both carry transparent bodies over {@code select}, so a
 * read is a FUNCTION of its key -- {@link #readIsFunctional} -- and a fact stated about one key
 * survives into an obligation -- {@link #factSurvivesIntoObligation}. A call to an opaque
 * {@code @Pure} method gives neither: it is re-havoc'd at every use. This is the reference-typed
 * counterpart of what {@link IntMapReads} pins for the int-to-int specialization.
 *
 * <p>{@link #distinctKeysAreUnrelatedShouldFAIL} and
 * {@link #containsSaysNothingAboutAnotherKeyShouldFAIL} are the controls: a map says nothing about
 * a key you have not constrained. Without them the positive cases could pass vacuously.
 *
 * <p>The value type is the {@code @Impure} {@link Ref} rather than {@code String} only so that
 * {@code ==} is available on a read: JVerify allows {@code ==} only when an operand's type is
 * impure, so {@code m.get(k) == m.get(k)} over {@code Map<String, String>} is refused as an
 * equality, not as a map operation. {@link #stringTypeArguments} covers the plain
 * {@code Map<String, String>} spelling with the operation that needs no equality on values.
 *
 * <p>{@link #readWithoutMembership} records that {@code get} carries NO membership obligation:
 * reading a key that was never shown to be present is permitted and yields an unconstrained value.
 * Laurel's partial map is a total map into {@code $MapEntry}, so there is no partiality to
 * discharge at the read. Pinned so that gaining or losing that obligation is a visible change.
 */
// The bytecode frontend does not translate JVerify.Map yet: the Map-typed parameters fail
// to translate and no verdict is produced.
@JVerifyTest(expectedToFail = true, blockedBy = Blocker.FRONTEND_OTHER,
        exitCode = 4, methodsVerified = 7, errorCount = 2)
class MapsVerification {

    /**
     * A reference type on which {@code ==} is reference equality. {@code @Impure} is what unlocks
     * {@code ==}; nothing about the map needs it.
     */
    @Impure
    interface Ref {
    }

    static void readIsFunctional(JVerify.Map<Ref, Ref> m, Ref k) {
        precondition(m.contains(k));
        check(m.get(k) == m.get(k));
    }

    static void readWithoutMembership(JVerify.Map<Ref, Ref> m, Ref k) {
        check(m.get(k) == m.get(k));
    }

    static void factSurvivesIntoObligation(JVerify.Map<Ref, Ref> m, Ref k, Ref v) {
        precondition(m.contains(k));
        precondition(m.get(k) == v);
        check(m.get(k) == v);
    }

    static void keyEqualityTransfers(JVerify.Map<Ref, Ref> m, Ref i, Ref j, Ref v) {
        precondition(m.contains(i));
        precondition(m.get(i) == v);
        precondition(i == j);
        check(m.get(j) == v);
    }

    static void containsIsFunctional(JVerify.Map<Ref, Ref> m, Ref k) {
        check(m.contains(k) == m.contains(k));
    }

    static void stringTypeArguments(JVerify.Map<String, String> m, String k) {
        precondition(m.contains(k));
        check(m.contains(k) == m.contains(k));
    }

    static void distinctKeysAreUnrelatedShouldFAIL(JVerify.Map<Ref, Ref> m, Ref i, Ref j, Ref v) {
        precondition(m.contains(i));
        precondition(m.contains(j));
        precondition(m.get(i) == v);
        check(m.get(j) == v);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void containsSaysNothingAboutAnotherKeyShouldFAIL(JVerify.Map<String, String> m,
                                                             String i, String j) {
        precondition(m.contains(i));
        check(m.contains(j));
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
