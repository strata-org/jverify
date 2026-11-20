package org.strata.jverify.verifier.tests.verification.customerUseCases.compareToCI;

import org.strata.jverify.JVerify;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.decreases;
import static org.strata.jverify.JVerify.invariant;
import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * The target: the WHOLE method against the whole specification.
 *
 * <p>The spec is the one {@code compareToCIImpl} is supposed to meet -- the sign of a
 * lexicographic comparison of the two case-normalised code-point sequences, with a
 * shorter-is-less tiebreak. {@link #compareSpec} states it directly, decoding one code point per
 * step, and {@link #compareToCIImpl} claims it as a postcondition.
 *
 * <p>Skipped, not xfailed, because the blocker's diagnostic cannot be pinned. A recursive
 * {@code @Transparent} method becomes a Laurel function, and Strata rejects a recursive function with no
 * measure:
 *
 * <pre>
 *   &lt;unknown&gt;(1:1-1:1): Error: recursive function 'FullSpec?static?compareSpec$asFunction'
 *           requires a 'decreases' clause or a '@[cases]' parameter for termination checking
 * </pre>
 *
 * That lands on no source range, so no {@code ^^^} marker can claim it, and the suite's
 * {@code jverify.strictSourceRanges} would fail on it. The front end discards the
 * {@code decreases} clause below, and {@code LaurelToCoreSchemaPass} consumes a measure only for
 * {@code While} loops, never for a
 * function; its own comment says so:
 *
 * <pre>
 *   -- TODO: Use the decreases of the function to determine where to put @[cases]
 * </pre>
 *
 * Core today terminates a recursive function only via {@code @[cases]} on a parameter whose type
 * is a datatype with constructors, and JVerify emits no Laurel datatypes, so that route is not
 * reachable from Java either. Unskip once Strata honours a function's measure. Note that this
 * makes even the single-component measure below inert -- it is emitted into Laurel, so the front
 * end does not warn about it, but nothing downstream consumes it until that TODO lands.
 *
 * <p>Two further things are deliberate rather than incidental. The case fold is a ghost
 * {@code IntMap} because {@code Character.toUpperCase}/{@code toLowerCase} are opaque and so are
 * not even functions -- two calls on one argument need not agree -- which makes the theorem
 * relative to an abstract fold rather than to the Unicode tables. And no helper here states a
 * postcondition: any {@code ensures} on a {@code @Transparent} method demotes it from transparent to
 * opaque, which costs congruence, so bounds obligations live at the call sites instead.
 *
 * <p>Even once the measure lands, the loop invariant relating the whole comparison to the
 * comparison of the unconsumed suffixes will not be found by SMT; it needs one-step unfolding
 * lemmas invoked as ghost calls. Postconditions do transfer to callers, so that is expressible,
 * but it is proof engineering rather than a missing feature.
 */
@JVerifyTest(skip = "STRATA_DECREASES: Strata honours a `decreases` measure only on a While loop,"
        + " not on a recursive function, so compareSpec is rejected for termination with an"
        + " unattributable diagnostic. See the class comment.")
class FullSpec {

    @Transparent
    static boolean isHigh(@Unbounded int c) {
        return 55296 <= c && c <= 56319;
    }

    @Transparent
    static boolean isLow(@Unbounded int c) {
        return 56320 <= c && c <= 57343;
    }

    @Transparent
    static boolean isSurrogate(@Unbounded int c) {
        return 55296 <= c && c <= 57343;
    }

    @Transparent
    static @Unbounded int toCodePoint(@Unbounded int high, @Unbounded int low) {
        return (high - 55296) * 1024 + (low - 56320) + 65536;
    }

    @Transparent
    static @Unbounded int sign(@Unbounded int v) {
        if (v > 0) {
            return 1;
        }
        if (v < 0) {
            return 0 - 1;
        }
        return 0;
    }

    @Transparent
    static boolean pairsAt(JVerify.IntMap m, @Unbounded int i, @Unbounded int len) {
        return isHigh(m.get(i)) && i + 1 < len && isLow(m.get(i + 1));
    }

    @Transparent
    static @Unbounded int headCodePoint(JVerify.IntMap m, @Unbounded int i, @Unbounded int len) {
        if (pairsAt(m, i, len)) {
            return toCodePoint(m.get(i), m.get(i + 1));
        }
        return m.get(i);
    }

    @Transparent
    static @Unbounded int headWidth(JVerify.IntMap m, @Unbounded int i, @Unbounded int len) {
        if (pairsAt(m, i, len)) {
            return 2;
        }
        return 1;
    }

    @Transparent
    static @Unbounded int compareSpec(JVerify.IntMap fold,
                                      JVerify.IntMap a, @Unbounded int ai, @Unbounded int alen,
                                      JVerify.IntMap b, @Unbounded int bi, @Unbounded int blen) {
        precondition(0 <= ai && ai <= alen);
        precondition(0 <= bi && bi <= blen);
        decreases((alen - ai) + (blen - bi));
        if (ai >= alen || bi >= blen) {
            return sign((alen - ai) - (blen - bi));
        }
        @Unbounded int ca = fold.get(headCodePoint(a, ai, alen));
        @Unbounded int cb = fold.get(headCodePoint(b, bi, blen));
        if (ca != cb) {
            return sign(ca - cb);
        }
        return compareSpec(fold,
                a, ai + headWidth(a, ai, alen), alen,
                b, bi + headWidth(b, bi, blen), blen);
    }

    static @Unbounded int compareToCIImpl(JVerify.IntMap fold,
                                          JVerify.IntMap a, @Unbounded int alen,
                                          JVerify.IntMap b, @Unbounded int blen) {
        precondition(alen >= 0);
        precondition(blen >= 0);
        postcondition((int r) -> sign(r) == compareSpec(fold, a, 0, alen, b, 0, blen));

        @Unbounded int k1 = 0;
        @Unbounded int k2 = 0;
        while (k1 < alen && k2 < blen) {
            invariant(0 <= k1 && k1 <= alen);
            invariant(0 <= k2 && k2 <= blen);
            invariant(compareSpec(fold, a, 0, alen, b, 0, blen)
                    == compareSpec(fold, a, k1, alen, b, k2, blen));

            @Unbounded int cp1 = a.get(k1);
            @Unbounded int cp2 = b.get(k2);

            if (!isSurrogate(cp1) && !isSurrogate(cp2)) {
                if (fold.get(cp1) != fold.get(cp2)) {
                    return sign(fold.get(cp1) - fold.get(cp2));
                }
                k1 = k1 + 1;
                k2 = k2 + 1;
                continue;
            }
            @Unbounded int w1 = headWidth(a, k1, alen);
            @Unbounded int w2 = headWidth(b, k2, blen);
            @Unbounded int d1 = fold.get(headCodePoint(a, k1, alen));
            @Unbounded int d2 = fold.get(headCodePoint(b, k2, blen));
            if (d1 != d2) {
                return sign(d1 - d2);
            }
            k1 = k1 + w1;
            k2 = k2 + w2;
        }
        return sign((alen - k1) - (blen - k2));
    }
}
