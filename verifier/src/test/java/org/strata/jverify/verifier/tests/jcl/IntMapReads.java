package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.JVerify;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

/**
 * What {@code JVerify.IntMap} buys, and the one thing it does not.
 *
 * <p>A specification over a string needs an indexed read that is a FUNCTION of its index. Nothing
 * else available gives one: array types do not translate, and a call to an opaque {@code @Pure}
 * method is re-havoc'd at every use, so even a fact stated about one element does not survive into
 * an obligation. {@code IntMap} lowers to Laurel's total map and {@code get} to {@code select},
 * whose body is transparent, so reads are stable -- {@link #readIsFunctional} and
 * {@link #factSurvivesIntoObligation} are the properties
 * {@code customerUseCases.compareToCI.FullSpec} is built on (named rather than linked: it is
 * package-private in another package).
 *
 * <p>{@link #distinctKeysAreUnrelatedShouldFAIL} is the control: a total map says nothing about a
 * key you have not constrained. Without it the positive cases could pass vacuously.
 *
 * <p>{@link #plainIntBindingShouldFAIL} pins the deliberate cost of the modelling choice. An
 * {@code IntMap} value is the UNCONSTRAINED Laurel integer, because a constrained one would demote
 * {@code select} from transparent to opaque and lose the very property above. So a plain
 * {@code int} binding carries an {@code int32} range that cannot be discharged, and callers must
 * bind to {@code @Unbounded int}, which the signature of {@code get} states. Pinned here so the
 * requirement cannot regress into a surprise.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 4, errorCount = 2)
class IntMapReads {

    static void readIsFunctional(JVerify.IntMap m, @Unbounded int i) {
        check(m.get(i) == m.get(i));
    }

    static void factSurvivesIntoObligation(JVerify.IntMap m, @Unbounded int i) {
        precondition(m.get(i) == 5);
        check(m.get(i) == 5);
    }

    static void keyEqualityTransfers(JVerify.IntMap m, @Unbounded int i, @Unbounded int j) {
        precondition(m.get(i) == 5);
        precondition(i == j);
        check(m.get(j) == 5);
    }

    static void distinctKeysAreUnrelatedShouldFAIL(JVerify.IntMap m) {
        precondition(m.get(0) == 1);
        check(m.get(1) == 1);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void plainIntBindingShouldFAIL(JVerify.IntMap m) {
        int narrowed = m.get(0);
//      ^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
        check(narrowed == m.get(0));
    }
}
