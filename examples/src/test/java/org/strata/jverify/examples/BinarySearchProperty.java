package org.strata.jverify.examples;

import org.strata.jverify.AsProperty;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Provide;

import static org.strata.jverify.JVerify.*;

/**
 * Advanced @AsProperty example: a precondition (sortedness) that is expensive
 * to satisfy by rejection sampling, handled by generating valid inputs
 * directly with a jqwik {@code @Provide} generator.
 *
 * <p>The contract is written in plain executable Java (the {@code isSorted}
 * and {@code contains} helpers below), because we are <em>testing</em> this
 * contract rather than verifying it — see "Testing unverified contracts".</p>
 *
 * <p>{@code binarySearch} uses a loop, so it cannot be {@code @Pure}; it does
 * not mutate any object, but {@code contracts2jqwik} cannot confirm that, so
 * generating the property in {@link BinarySearchPropertyGenerated} emits a
 * frame soundness warning. The translation still proceeds: the property checks
 * the postcondition, and we know by inspection the method mutates nothing.</p>
 */
class BinarySearchProperty {

    @AsProperty
    int binarySearch(@ForAll("sortedArrays") int[] arr, @ForAll int target) {
        precondition(isSorted(arr));
        postcondition((Integer r) -> contains(arr, target)
            ? 0 <= r && r < arr.length && arr[r] == target
            : r == -1);

        int lo = 0;
        int hi = arr.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] == target) {
                return mid;
            }
            if (arr[mid] < target) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return -1;
    }

    /**
     * jqwik generator: produce sorted arrays directly instead of generating
     * random arrays and discarding the (overwhelming majority of) unsorted ones.
     */
    @Provide
    Arbitrary<int[]> sortedArrays() {
        return Arbitraries.integers().between(-50, 50)
            .array(int[].class).ofMaxSize(16)
            .map(a -> {
                java.util.Arrays.sort(a);
                return a;
            });
    }

    // Executable contract helpers. We are testing this contract, not verifying
    // it, so these are ordinary Java rather than @Pure/@Erased spec helpers.
    static boolean isSorted(int[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1] > a[i]) {
                return false;
            }
        }
        return true;
    }

    static boolean contains(int[] a, int t) {
        for (int x : a) {
            if (x == t) {
                return true;
            }
        }
        return false;
    }
}
