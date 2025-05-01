package com.aws.jverify.examples;

import com.aws.jverify.AsProperty;
import com.aws.jverify.Erased;
import com.aws.jverify.Pure;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import static com.aws.jverify.JVerify.*;

// @JVerifyTest
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

// Skipping because jqwik isn't on the classpath when compiling/running tests
// TEST: skip
