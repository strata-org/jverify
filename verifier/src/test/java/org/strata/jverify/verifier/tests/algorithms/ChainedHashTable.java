package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_ARRAYS, Blocker.STRATA_NULLABLE}, exitCode = 0)
class ChainedHashTable {
    static class Entry {
        int key;
        int value;
        @Nullable Entry next;
    }

    @Pure
    @Erased
    static int bucketOf(int key, int buckets) {
        precondition(buckets > 0);
        return ((key % buckets) + buckets) % buckets;
    }

    @Pure
    @Erased
    static boolean chainIn(@Nullable Entry e, int bucket, int buckets, @Unbounded int len) {
        reads(everything());
        decreases(len);
        precondition(buckets > 0);
        if (e == null) {
            return len == 0;
        }
        return len > 0 && bucketOf(e.key, buckets) == bucket && chainIn(e.next, bucket, buckets, len - 1);
    }

    @Pure
    @Erased
    static boolean wellFormed(@Nullable Entry[] table, int[] lengths) {
        reads(everything());
        return table.length > 0 && table.length == lengths.length
                && forall((int b) -> implies(0 <= b && b < table.length,
                    lengths[b] >= 0 && chainIn(table[b], b, table.length, lengths[b])));
    }

    @Pure
    @Erased
    static boolean chainHas(@Nullable Entry e, int key, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (e == null || len <= 0) {
            return false;
        }
        return e.key == key || chainHas(e.next, key, len - 1);
    }

    @Pure
    @Erased
    static int chainValue(@Nullable Entry e, int key, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (e == null || len <= 0) {
            return 0;
        }
        return e.key == key ? e.value : chainValue(e.next, key, len - 1);
    }

    static void put(@Nullable Entry[] table, int[] lengths, int key, int value) {
        precondition(wellFormed(table, lengths));
        precondition(forall((int b) -> implies(0 <= b && b < lengths.length, lengths[b] < 1000000)));
        modifies(everything());
        postcondition(wellFormed(table, lengths));
        postcondition(chainHas(table[bucketOf(key, table.length)], key, lengths[bucketOf(key, table.length)]));
        postcondition(chainValue(table[bucketOf(key, table.length)], key, lengths[bucketOf(key, table.length)]) == value);

        int b = bucketOf(key, table.length);
        @Nullable Entry e = table[b];
        while (e != null) {
            if (e.key == key) {
                e.value = value;
                return;
            }
            e = e.next;
        }
        Entry fresh = new Entry();
        fresh.key = key;
        fresh.value = value;
        fresh.next = table[b];
        table[b] = fresh;
        lengths[b] = lengths[b] + 1;
    }

    static boolean get(@Nullable Entry[] table, int[] lengths, int key) {
        precondition(wellFormed(table, lengths));
        postcondition((boolean res) -> res == chainHas(table[bucketOf(key, table.length)], key, lengths[bucketOf(key, table.length)]));

        int b = bucketOf(key, table.length);
        @Nullable Entry e = table[b];
        while (e != null) {
            if (e.key == key) {
                return true;
            }
            e = e.next;
        }
        return false;
    }
}
