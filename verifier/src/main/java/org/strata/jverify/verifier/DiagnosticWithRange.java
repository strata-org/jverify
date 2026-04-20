package org.strata.jverify.verifier;

import org.strata.jverify.common.Range;

public interface DiagnosticWithRange {
    public Range getRange();
    String filePath();
    String filename();
}
