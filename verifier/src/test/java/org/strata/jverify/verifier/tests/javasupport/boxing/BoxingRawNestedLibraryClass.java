// Reference comparison of the results of a raw nested generic library class
// (Map.Entry, described by the builtin contracts): the raw type may carry a box
// to the erased result, so the comparison is refused as one on boxes.
package org.strata.jverify.verifier.tests.javasupport.boxing;

import java.util.Map;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(useBuiltinContracts = true, continueOnErrors = true, exitCode = 0, methodsInvalid = 0, errorCount = 0,
        methodsVerified = 1, methodsSkipped = 1)
class BoxingRawNestedLibraryClass {
    @SuppressWarnings("rawtypes")
    static boolean sameRawEntryValue(Map.Entry e, Map.Entry f) {
        return e.getValue() == f.getValue();
//                          ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }
}
