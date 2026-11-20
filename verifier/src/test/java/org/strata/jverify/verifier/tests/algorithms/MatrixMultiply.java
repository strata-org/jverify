package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_ARRAYS}, exitCode = 0)
class MatrixMultiply {
    @Transparent
    @Erased
    static boolean square(int[][] m, int n) {
        reads(m);
        return m.length == n && forall((int r) -> implies(0 <= r && r < n, m[r].length == n));
    }

    @Transparent
    @Erased
    static boolean bounded(int[][] m, int n) {
        reads(m);
        return forall((int r, int c) -> implies(0 <= r && r < n && 0 <= c && c < n,
                -100 <= m[r][c] && m[r][c] <= 100));
    }

    @Transparent
    @Erased
    static @Unbounded int dot(int[][] a, int[][] b, int i, int j, @Unbounded int k) {
        reads(a);
        reads(b);
        decreases(k);
        precondition(0 <= i && i < a.length && 0 <= j && 0 <= k && k <= a[i].length && k <= b.length);
        precondition(forall((int r) -> implies(0 <= r && r < k, j < b[r].length)));
        if (k <= 0) {
            return 0;
        }
        return dot(a, b, i, j, k - 1) + a[i][k - 1] * b[k - 1][j];
    }

    @Transparent
    @Erased
    static boolean productBelow(int[][] c, int[][] a, int[][] b, int n, int rows, int cols) {
        reads(c);
        reads(a);
        reads(b);
        return square(a, n) && square(b, n) && forall((int r, int col) -> implies(0 <= r && r < n && 0 <= col && col < n
                && (r < rows || (r == rows && col < cols)), c[r][col] == dot(a, b, r, col, n)));
    }

    static void multiply(int[][] a, int[][] b, int[][] c, int n) {
        precondition(0 <= n && n <= 10);
        precondition(square(a, n) && square(b, n) && square(c, n));
        precondition(bounded(a, n) && bounded(b, n));
        precondition(c != a && c != b);
        precondition(forall((int i, int k) -> implies(0 <= i && i < n && 0 <= k && k < n, c[i] != a[k] && c[i] != b[k] && (i == k || c[i] != c[k]))));
        modifies(c);
        postcondition(productBelow(c, a, b, n, n, 0));

        int i = 0;
        while (i < n) {
            modifies(c);
            invariant(0 <= i && i <= n);
            invariant(productBelow(c, a, b, n, i, 0));

            int j = 0;
            while (j < n) {
                modifies(c);
                invariant(0 <= j && j <= n);
                invariant(productBelow(c, a, b, n, i, j));

                int s = 0;
                int k = 0;
                while (k < n) {
                    invariant(0 <= k && k <= n);
                    invariant(s == dot(a, b, i, j, k));
                    invariant(-10000 * k <= s && s <= 10000 * k);

                    s = s + a[i][k] * b[k][j];
                    k = k + 1;
                }
                c[i][j] = s;
                j = j + 1;
            }
            i = i + 1;
        }
    }
}
