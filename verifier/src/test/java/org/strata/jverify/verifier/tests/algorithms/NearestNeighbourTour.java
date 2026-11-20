package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_ARRAYS}, exitCode = 0)
class NearestNeighbourTour {
    @Transparent
    @Erased
    static boolean square(int[][] d, int n) {
        reads(d);
        return d.length == n && forall((int r) -> implies(0 <= r && r < n, d[r].length == n));
    }

    @Transparent
    @Erased
    static boolean distinctValidBelow(int[] tour, int n, int hi) {
        reads(tour);
        return forall((int k) -> implies(0 <= k && k < hi, 0 <= tour[k] && tour[k] < n))
                && forall((int k, int m) -> implies(0 <= k && k < m && m < hi, tour[k] != tour[m]));
    }

    @Transparent
    @Erased
    static boolean visitedMatches(boolean[] visited, int[] tour, int hi) {
        reads(visited);
        reads(tour);
        return forall((int c) -> implies(0 <= c && c < visited.length,
                visited[c] == exists((int k) -> 0 <= k && k < hi && tour[k] == c)));
    }

    static int[] tour(int[][] d, int n) {
        precondition(1 <= n && n <= 1000);
        precondition(square(d, n));
        postcondition((int[] res) -> res.length == n && res[0] == 0 && distinctValidBelow(res, n, n));

        int[] tour = new int[n];
        boolean[] visited = new boolean[n];
        tour[0] = 0;
        visited[0] = true;

        int step = 1;
        while (step < n) {
            modifies(tour);
            modifies(visited);
            invariant(1 <= step && step <= n);
            invariant(tour.length == n && visited.length == n);
            invariant(tour[0] == 0);
            invariant(distinctValidBelow(tour, n, step));
            invariant(visitedMatches(visited, tour, step));

            int last = tour[step - 1];
            int best = -1;
            int c = 0;
            while (c < n) {
                invariant(0 <= c && c <= n);
                invariant(-1 <= best && best < c);
                invariant(implies(best >= 0, !visited[best]));

                if (!visited[c] && (best == -1 || d[last][c] < d[last][best])) {
                    best = c;
                }
                c = c + 1;
            }
            tour[step] = best;
            visited[best] = true;
            step = step + 1;
        }
        return tour;
    }
}
