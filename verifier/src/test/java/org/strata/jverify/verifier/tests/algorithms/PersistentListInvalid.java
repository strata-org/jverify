package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 4)
class PersistentListInvalid {
    static final class Cell {
        int head;
        @Nullable Cell tail;

        Cell(int head, @Nullable Cell tail) {
            postcondition(this.head == head && this.tail == tail);
            this.head = head;
            this.tail = tail;
        }
    }

    @Transparent
    @Erased
    static boolean hasLength(@Nullable Cell c, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (c == null) {
            return len == 0;
        }
        return len > 0 && hasLength(c.tail, len - 1);
    }

    @Transparent
    @Erased
    static int valueAt(@Nullable Cell c, @Unbounded int k) {
        reads(everything());
        decreases(k);
        if (c == null) {
            return 0;
        }
        return k == 0 ? c.head : valueAt(c.tail, k - 1);
    }

    @Transparent
    @Erased
    static @Nullable Cell cellAt(@Nullable Cell c, @Unbounded int k) {
        reads(everything());
        decreases(k);
        if (c == null || k == 0) {
            return c;
        }
        return cellAt(c.tail, k - 1);
    }

    static Cell update(Cell list, @Unbounded int len, @Unbounded int k, int v) {
        precondition(hasLength(list, len));
        precondition(0 <= k && k < len);
        decreases(k);
        postcondition((Cell res) -> hasLength(res, len));
//                                  ^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition((Cell res) -> valueAt(res, k) == v);
        postcondition((Cell res) -> forall((int j) -> implies(0 <= j && j < len && j != k, valueAt(res, j) == valueAt(list, j))));
//                                  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition((Cell res) -> cellAt(res, k + 1) == cellAt(list, k + 1));
//                                  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition(forall((int j) -> implies(0 <= j && j < len, valueAt(list, j) == old(valueAt(list, j)))));

        if (k == 0) {
            return new Cell(v, list);
        }
        return new Cell(list.head, update(list.tail, len - 1, k - 1, v));
    }
}
