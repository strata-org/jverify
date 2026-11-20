package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_ARRAYS, Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 0)
class CheneyCopy {
    static class Obj {
        int payload;
        @Nullable Obj left;
        @Nullable Obj right;
        @Nullable Obj forward;
    }

    @Transparent
    @Erased
    static boolean member(Obj[] space, @Nullable Obj o) {
        reads(space);
        return o == null || exists((int i) -> 0 <= i && i < space.length && space[i] == o);
    }

    @Transparent
    @Erased
    static boolean closedUnforwarded(Obj[] from) {
        reads(everything());
        return forall((int i) -> implies(0 <= i && i < from.length,
                from[i] != null && from[i].forward == null && member(from, from[i].left) && member(from, from[i].right)));
    }

    @Transparent
    @Erased
    static @Nullable Obj image(@Nullable Obj o) {
        reads(everything());
        return o == null ? null : o.forward;
    }

    @Transparent
    @Erased
    static boolean isomorphicCopy(Obj[] from) {
        reads(everything());
        return forall((int i) -> implies(0 <= i && i < from.length && from[i].forward != null,
                from[i].forward.payload == from[i].payload
                && from[i].forward.left == image(from[i].left)
                && from[i].forward.right == image(from[i].right)
                && (from[i].left == null || from[i].left.forward != null)
                && (from[i].right == null || from[i].right.forward != null)));
    }

    @Transparent
    @Erased
    static boolean injective(Obj[] from) {
        reads(everything());
        return forall((int i, int j) -> implies(0 <= i && i < j && j < from.length
                && from[i].forward != null && from[j].forward != null, from[i].forward != from[j].forward));
    }

    static Obj collect(Obj[] from, Obj root, Obj[] to) {
        precondition(closedUnforwarded(from));
        precondition(member(from, root));
        precondition(to.length >= from.length);
        modifies(everything());
        postcondition((Obj res) -> res == root.forward);
        postcondition(isomorphicCopy(from));
        postcondition(injective(from));

        int free = 0;
        int scan = 0;
        Obj rootCopy = copyOf(root, to, free);
        free = free + 1;
        while (scan < free) {
            modifies(everything());
            invariant(0 <= scan && scan <= free && free <= to.length);

            Obj o = to[scan];
            if (o.left != null) {
                if (o.left.forward == null) {
                    copyOf(o.left, to, free);
                    free = free + 1;
                }
                o.left = o.left.forward;
            }
            if (o.right != null) {
                if (o.right.forward == null) {
                    copyOf(o.right, to, free);
                    free = free + 1;
                }
                o.right = o.right.forward;
            }
            scan = scan + 1;
        }
        return rootCopy;
    }

    static Obj copyOf(Obj o, Obj[] to, int free) {
        precondition(o.forward == null);
        precondition(0 <= free && free < to.length);
        modifies(everything());
        postcondition((Obj res) -> fresh(res) && o.forward == res && to[free] == res);
        postcondition((Obj res) -> res.payload == o.payload && res.left == o.left && res.right == o.right);

        Obj c = new Obj();
        c.payload = o.payload;
        c.left = o.left;
        c.right = o.right;
        o.forward = c;
        to[free] = c;
        return c;
    }
}
