package com.aws.jverify.verifier;

import com.aws.jverify.common.Range;

public interface DiagnosticWithRange {
    public Range getRange();
    String filePath();
    String filename();
}
