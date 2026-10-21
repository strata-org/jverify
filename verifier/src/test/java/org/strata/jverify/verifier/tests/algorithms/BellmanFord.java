package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR}, exitCode = 0)
class BellmanFord {
    static final int INF = 2000000000;

    @Pure
    @Erased
    static boolean wellFormed(int n, int[] eu, int[] ev, int[] ew) {
        reads(eu);
        reads(ev);
        reads(ew);
        return eu.length == ev.length && ev.length == ew.length
                && forall((int e) -> implies(0 <= e && e < eu.length,
                    0 <= eu[e] && eu[e] < n && 0 <= ev[e] && ev[e] < n && -1000 <= ew[e] && ew[e] <= 1000));
    }

    @Pure
    @Erased
    static boolean distBounded(int[] dist, int steps) {
        reads(dist);
        return forall((int x) -> implies(0 <= x && x < dist.length,
                dist[x] == INF || (-1000 * steps <= dist[x] && dist[x] <= 1000 * steps)));
    }

    @Pure
    @Erased
    static boolean relaxedBelow(int[] dist, int[] eu, int[] ev, int[] ew, int hi) {
        reads(dist);
        reads(eu);
        reads(ev);
        reads(ew);
        return forall((int e) -> implies(0 <= e && e < hi,
                dist[eu[e]] == INF || dist[ev[e]] <= dist[eu[e]] + ew[e]));
    }

    @Pure
    @Erased
    static boolean infiniteBelow(int[] dist, int hi) {
        reads(dist);
        return forall((int y) -> implies(0 <= y && y < hi, dist[y] == INF));
    }

    static boolean bellmanFord(int n, int[] eu, int[] ev, int[] ew, int src, int[] dist) {
        precondition(1 <= n && n <= 100);
        precondition(eu.length <= 100);
        precondition(wellFormed(n, eu, ev, ew));
        precondition(0 <= src && src < n);
        precondition(dist.length == n);
        modifies(dist);
        postcondition((boolean res) -> implies(res, relaxedBelow(dist, eu, ev, ew, eu.length)));

        int x = 0;
        while (x < n) {
            modifies(dist);
            invariant(0 <= x && x <= n);
            invariant(infiniteBelow(dist, x));

            dist[x] = INF;
            x = x + 1;
        }
        dist[src] = 0;

        int steps = 0;
        int round = 1;
        while (round < n) {
            modifies(dist);
            invariant(1 <= round && round <= n);
            invariant(0 <= steps && steps <= (round - 1) * eu.length);
            invariant(distBounded(dist, steps));

            int e = 0;
            while (e < eu.length) {
                modifies(dist);
                invariant(0 <= e && e <= eu.length);
                invariant(0 <= steps && steps <= (round - 1) * eu.length + e);
                invariant(distBounded(dist, steps));

                int u = eu[e];
                int v = ev[e];
                if (dist[u] != INF && dist[u] + ew[e] < dist[v]) {
                    dist[v] = dist[u] + ew[e];
                }
                steps = steps + 1;
                e = e + 1;
            }
            round = round + 1;
        }

        int e = 0;
        while (e < eu.length) {
            invariant(0 <= e && e <= eu.length);
            invariant(relaxedBelow(dist, eu, ev, ew, e));

            int u = eu[e];
            int v = ev[e];
            if (dist[u] != INF && dist[u] + ew[e] < dist[v]) {
                return false;
            }
            e = e + 1;
        }
        return true;
    }
}
