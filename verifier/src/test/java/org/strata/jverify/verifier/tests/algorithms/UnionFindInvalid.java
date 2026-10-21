package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 4)
class UnionFindInvalid {
    @Pure
    @Erased
    static boolean ranked(int[] parent, int[] rank) {
        reads(parent);
        reads(rank);
        return parent.length == rank.length && parent.length <= 1000000
                && forall((int i) -> implies(0 <= i && i < parent.length,
                    0 <= parent[i] && parent[i] < parent.length && 0 <= rank[i] && rank[i] < parent.length
                    && (parent[i] == i || rank[parent[i]] > rank[i])));
    }

    @Pure
    @Erased
    static int rootOf(int[] parent, int[] rank, int i) {
        reads(parent);
        reads(rank);
        decreases(parent.length - rank[i]);
        if (parent[i] == i) {
            return i;
        }
        return rootOf(parent, rank, parent[i]);
    }

    static int find(int[] parent, int[] rank, int x) {
        precondition(ranked(parent, rank));
        precondition(0 <= x && x < parent.length);
        modifies(parent);
        postcondition((int res) -> parent[res] == res && res == old(rootOf(parent, rank, x)));
        postcondition(ranked(parent, rank));
        postcondition(forall((int i) -> implies(0 <= i && i < parent.length,
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
                rootOf(parent, rank, i) == old(rootOf(parent, rank, i)))));

        int root = x;
        while (parent[root] != root) {
            invariant(0 <= root && root < parent.length);
            invariant(rootOf(parent, rank, root) == rootOf(parent, rank, x));
            root = parent[root];
        }
        int cur = x;
        while (parent[cur] != root) {
            modifies(parent);
            invariant(0 <= cur && cur < parent.length);
            invariant(ranked(parent, rank));
            invariant(rootOf(parent, rank, cur) == root);

            int next = parent[cur];
            parent[cur] = cur;
            cur = next;
        }
        return root;
    }

    static void union(int[] parent, int[] rank, int a, int b) {
        precondition(ranked(parent, rank));
        precondition(0 <= a && a < parent.length && 0 <= b && b < parent.length);
        precondition(forall((int i) -> implies(0 <= i && i < rank.length, rank[i] < parent.length - 1)));
        modifies(parent);
        modifies(rank);
        postcondition(rootOf(parent, rank, a) == rootOf(parent, rank, b));

        int ra = find(parent, rank, a);
        int rb = find(parent, rank, b);
        if (ra == rb) {
            return;
        }
        if (rank[ra] < rank[rb]) {
            parent[ra] = rb;
        } else if (rank[ra] > rank[rb]) {
            parent[rb] = ra;
        } else {
            parent[rb] = ra;
            rank[ra] = rank[ra] + 1;
        }
    }
}
