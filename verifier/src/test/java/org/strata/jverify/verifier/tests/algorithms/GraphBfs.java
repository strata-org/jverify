package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_ARRAYS}, exitCode = 0)
class GraphBfs {
    @Pure
    @Erased
    static boolean adjacencyWellFormed(int[][] adj) {
        reads(everything());
        return forall((int u, int k) -> implies(0 <= u && u < adj.length && 0 <= k && k < adj[u].length,
                0 <= adj[u][k] && adj[u][k] < adj.length));
    }

    @Pure
    @Erased
    static boolean closedUnderEdges(int[][] adj, boolean[] visited) {
        reads(everything());
        return forall((int u, int k) -> implies(0 <= u && u < adj.length && visited[u] && 0 <= k && k < adj[u].length,
                visited[adj[u][k]]));
    }

    @Pure
    @Erased
    static boolean edge(int[][] adj, int u, int v) {
        reads(everything());
        return 0 <= u && u < adj.length && exists((int k) -> 0 <= k && k < adj[u].length && adj[u][k] == v);
    }

    @Pure
    @Erased
    static boolean parentsJustify(int[][] adj, boolean[] visited, int[] parent, int src) {
        reads(everything());
        return forall((int v) -> implies(0 <= v && v < adj.length && visited[v] && v != src,
                0 <= parent[v] && parent[v] < adj.length && visited[parent[v]] && edge(adj, parent[v], v)));
    }

    static boolean[] bfs(int[][] adj, int src, int[] parent) {
        precondition(adjacencyWellFormed(adj));
        precondition(0 <= src && src < adj.length);
        precondition(parent.length == adj.length);
        precondition(forall((int u) -> implies(0 <= u && u < adj.length, adj[u] != parent)));
        modifies(parent);
        postcondition((boolean[] res) -> res.length == adj.length && res[src]);
        postcondition((boolean[] res) -> closedUnderEdges(adj, res));
        postcondition((boolean[] res) -> parentsJustify(adj, res, parent, src));

        int n = adj.length;
        boolean[] visited = new boolean[n];
        int[] queue = new int[n];
        int head = 0;
        int tail = 0;
        visited[src] = true;
        queue[tail] = src;
        tail = tail + 1;
        while (head < tail) {
            modifies(visited);
            modifies(queue);
            modifies(parent);
            invariant(0 <= head && head <= tail && tail <= n);
            invariant(visited[src]);
            invariant(parentsJustify(adj, visited, parent, src));

            int u = queue[head];
            head = head + 1;
            int k = 0;
            while (k < adj[u].length) {
                modifies(visited);
                modifies(queue);
                modifies(parent);
                invariant(0 <= k && k <= adj[u].length);
                invariant(0 <= head && head <= tail && tail <= n);
                invariant(parentsJustify(adj, visited, parent, src));

                int v = adj[u][k];
                if (!visited[v]) {
                    visited[v] = true;
                    parent[v] = u;
                    queue[tail] = v;
                    tail = tail + 1;
                }
                k = k + 1;
            }
        }
        return visited;
    }
}
