package org.strata.jverify.examples;

import org.strata.jverify.AsProperty;
import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(skip = "Skipping because jqwik isn't on the classpath when compiling/running tests")
class BinarySearchProperty {
    
    @AsProperty
    int binarySearch(@ForAll int[] arr, @ForAll int target) {
      precondition(sorted(arr));
      postcondition((Integer r) -> sequence(arr).contains(target) 
        ? 0 <= r && r < arr.length && arr[r] == target 
        : r == -1
      );
    
      throw new RuntimeException("not yet implemented");
    }
    
    // Generated because of the above @AsProperty
    @Property
    boolean binarySearchProperty(@ForAll int[] values, @ForAll int target) {
        Assume.that(sorted(values));

        var r = binarySearch(values, target);
        return sequence(values).contains(target)
                ? 0 <= r && r < values.length && values[r] == target
                : r == -1;
    }
    
    @Erased
    @Pure
    boolean sorted(int[] arr)
    {
        reads(arr);

        return forall((Integer i) -> forall((Integer j) ->
                implies(0 <= i && i < j && j < arr.length, arr[i] < arr[j])));
    }

}
