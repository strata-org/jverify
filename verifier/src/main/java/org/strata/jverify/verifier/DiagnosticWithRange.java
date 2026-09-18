package org.strata.jverify.verifier;

import org.strata.jverify.common.Range;

import java.net.URI;

public interface DiagnosticWithRange {
    public Range getRange();
    String filePath();
    String filename();

    /**
     * The source file this diagnostic points at.
     *
     * <p>Diagnostics produced from Strata's output are not attached to a
     * {@link javax.tools.JavaFileObject}, so {@link javax.tools.Diagnostic#getSource()}
     * returns {@code null} for them. Callers that need to tell one input file from
     * another — the test harness attributing a diagnostic to the file that
     * declared it, for instance — should use this instead.
     */
    URI getUri();
}
