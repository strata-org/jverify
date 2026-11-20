package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(skip = "Core symbolicEval runs out of memory on the recursive @Transparent openAt (P552053383)", exitCode = 0)
class XmlNesting {
    @Transparent
    @Erased
    static boolean tags(int[] events) {
        reads(events);
        return forall((int k) -> implies(0 <= k && k < events.length, events[k] != 0 && events[k] != -2147483648));
    }

    @Transparent
    @Erased
    static @Unbounded int depthAfter(int[] events, @Unbounded int hi) {
        reads(events);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        return depthAfter(events, hi - 1) + (events[hi - 1] > 0 ? 1 : -1);
    }

    @Transparent
    @Erased
    static int openAt(int[] events, @Unbounded int hi, @Unbounded int d) {
        reads(events);
        decreases(hi);
        if (hi <= 0) {
            return 0;
        }
        if (events[hi - 1] > 0 && d == depthAfter(events, hi - 1)) {
            return events[hi - 1];
        }
        return openAt(events, hi - 1, d);
    }

    @Transparent
    @Erased
    static boolean matchedBelow(int[] events, int hi) {
        reads(events);
        return forall((int k) -> implies(0 <= k && k < hi && events[k] < 0,
                depthAfter(events, k) > 0 && openAt(events, k, depthAfter(events, k) - 1) == -events[k]));
    }

    @Transparent
    @Erased
    static boolean stackModels(int[] events, int[] stack, int depth, int hi) {
        reads(events);
        reads(stack);
        return forall((int d) -> implies(0 <= d && d < depth, stack[d] == openAt(events, hi, d)));
    }

    static boolean wellNested(int[] events, int[] stack) {
        precondition(tags(events));
        precondition(stack.length >= events.length);
        precondition(events.length <= 1000000);
        precondition(events != stack);
        modifies(stack);
        postcondition((boolean res) -> res == (matchedBelow(events, events.length) && depthAfter(events, events.length) == 0));

        int depth = 0;
        int i = 0;
        while (i < events.length) {
            modifies(stack);
            invariant(0 <= i && i <= events.length);
            invariant(0 <= depth && depth <= i);
            invariant(depth == depthAfter(events, i));
            invariant(matchedBelow(events, i));
            invariant(stackModels(events, stack, depth, i));

            int e = events[i];
            if (e > 0) {
                stack[depth] = e;
                depth = depth + 1;
            } else {
                if (depth == 0 || stack[depth - 1] != -e) {
                    return false;
                }
                depth = depth - 1;
            }
            i = i + 1;
        }
        return depth == 0;
    }
}
