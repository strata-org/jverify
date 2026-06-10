package org.strata.jverify.examples;

import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.strata.jverify.AsProperty;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import static org.strata.jverify.JVerify.*;

public class BinarySearchPropertyGenerated extends BinarySearchProperty {

    // Generated from BinarySearchProperty.java by contracts2jqwik. Do not edit by hand;
    // regenerate by re-running contracts2jqwik on the source file.
    @Property
    public boolean binarySearchProperty(@ForAll("sortedArrays") int[] arr, @ForAll int target) {
        Assume.that(isSorted(arr));
        int r = binarySearch(arr, target);
        return (contains(arr, target) ? 0 <= r && r < arr.length && arr[r] == target : r == -1);
    }
}
