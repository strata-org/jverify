package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_ARRAYS, Blocker.FRONTEND_OTHER}, exitCode = 0)
class DijkstraHeap {
    static final int INF = 2000000000;

    @Pure
    @Erased
    static boolean weights(int[][] w, int n) {
        reads(everything());
        return w.length == n && forall((int u, int v) -> implies(0 <= u && u < n && 0 <= v && v < n,
                w[u].length == n && -1 <= w[u][v] && w[u][v] <= 1000));
    }

    @Pure
    @Erased
    static boolean relaxed(int[][] w, int[] dist, int n) {
        reads(everything());
        return forall((int u, int v) -> implies(0 <= u && u < n && 0 <= v && v < n && w[u][v] >= 0 && dist[u] != INF,
                dist[v] <= dist[u] + w[u][v]));
    }

    @Pure
    @Erased
    static boolean heapOrdered(int[] key, int size) {
        reads(key);
        return forall((int k) -> implies(0 < k && k < size, key[(k - 1) / 2] <= key[k]));
    }

    @Pure
    @Erased
    static boolean pendingCovered(int[][] w, int[] dist, boolean[] done, int[] key, int[] vert, int size, int n) {
        reads(everything());
        return forall((int u) -> implies(0 <= u && u < n && !done[u] && dist[u] != INF,
                exists((int k) -> 0 <= k && k < size && vert[k] == u && key[k] == dist[u])));
    }

    static void push(int[] key, int[] vert, int size, int k, int v) {
        precondition(0 <= size && size < key.length && key.length == vert.length);
        precondition(heapOrdered(key, size));
        precondition(key != vert);
        modifies(key);
        modifies(vert);
        postcondition(heapOrdered(key, size + 1));

        int i = size;
        key[i] = k;
        vert[i] = v;
        while (i > 0 && key[(i - 1) / 2] > key[i]) {
            modifies(key);
            modifies(vert);
            invariant(0 <= i && i <= size);
            int p = (i - 1) / 2;
            int tk = key[p];
            key[p] = key[i];
            key[i] = tk;
            int tv = vert[p];
            vert[p] = vert[i];
            vert[i] = tv;
            i = p;
        }
    }

    static void popInto(int[] key, int[] vert, int size) {
        precondition(0 < size && size <= key.length && key.length == vert.length);
        precondition(heapOrdered(key, size));
        precondition(key != vert);
        modifies(key);
        modifies(vert);
        postcondition(heapOrdered(key, size - 1));

        int last = size - 1;
        key[0] = key[last];
        vert[0] = vert[last];
        int i = 0;
        while (2 * i + 1 < last) {
            modifies(key);
            modifies(vert);
            invariant(0 <= i && i < last);
            int c = 2 * i + 1;
            if (c + 1 < last && key[c + 1] < key[c]) {
                c = c + 1;
            }
            if (key[i] <= key[c]) {
                return;
            }
            int tk = key[i];
            key[i] = key[c];
            key[c] = tk;
            int tv = vert[i];
            vert[i] = vert[c];
            vert[c] = tv;
            i = c;
        }
    }

    static int[] shortestPaths(int[][] w, int n, int src) {
        precondition(1 <= n && n <= 100);
        precondition(weights(w, n));
        precondition(0 <= src && src < n);
        postcondition((int[] dist) -> dist.length == n && dist[src] == 0);
        postcondition((int[] dist) -> relaxed(w, dist, n));

        int[] dist = new int[n];
        boolean[] done = new boolean[n];
        int[] key = new int[n * n + 1];
        int[] vert = new int[n * n + 1];
        int x = 0;
        while (x < n) {
            modifies(dist);
            invariant(0 <= x && x <= n);
            dist[x] = INF;
            x = x + 1;
        }
        dist[src] = 0;
        int size = 0;
        push(key, vert, size, 0, src);
        size = size + 1;

        while (size > 0) {
            modifies(everything());
            invariant(0 <= size && size <= n * n + 1);
            invariant(heapOrdered(key, size));
            invariant(pendingCovered(w, dist, done, key, vert, size, n));

            int u = vert[0];
            int du = key[0];
            popInto(key, vert, size);
            size = size - 1;
            if (done[u] || du != dist[u]) {
                continue;
            }
            done[u] = true;
            int v = 0;
            while (v < n) {
                modifies(everything());
                invariant(0 <= v && v <= n);
                if (w[u][v] >= 0 && dist[u] + w[u][v] < dist[v]) {
                    dist[v] = dist[u] + w[u][v];
                    if (size < key.length) {
                        push(key, vert, size, dist[v], v);
                        size = size + 1;
                    }
                }
                v = v + 1;
            }
        }
        return dist;
    }
}
