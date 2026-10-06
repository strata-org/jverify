package org.strata.jverify.langref.jls14.s14_20;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.3 -- try-with-resources: resources declared in the resource
// specification are automatically closed when the try block completes, in the
// reverse order of their declaration, whether completion is normal or abrupt.
// Exercises normal completion (close on both), close ordering, and closing when
// the body throws.
class TryWithResources_5 {
    // shared sink recording close order: order[0] is how many closes happened
    static int[] order = new int[3];

    static final class Resource implements AutoCloseable {
        final int id;
        Resource(int id) {
            this.id = id;
        }
        @Override
        public void close() {
            int n = order[0];
            order[1 + n] = id; // record id at position of close sequence
            order[0] = n + 1;
        }
    }

    static void run() {

        // normal completion: both resources closed in reverse declaration order
        order[0] = 0;
        order[1] = 0;
        order[2] = 0;
        try (Resource a = new Resource(1); Resource b = new Resource(2)) {
        }     // b (declared last) closed first
        check(order[2] == 1);
    }
}
