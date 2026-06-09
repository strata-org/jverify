package org.strata.jverify.verifier.laurel;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Windows-safety regression (#439): a Strata diagnostic reported against the
 * synthetic {@code <unknown>} source path must not be routed through
 * {@code Paths.get}, which throws {@code InvalidPathException} on Windows
 * ({@code '<'}/{@code '>'} are illegal filename characters). The directly
 * constructed URI must still expose a {@code /<unknown>} path so the
 * JavaToLaurelCompiler line-map fallback (a path-suffix match) triggers.
 */
class LaurelDriverUriTest {

    @Test
    void syntheticFileUriForUnknownPathIsConstructibleAndFallbackMatchable() {
        URI uri = LaurelDriver.syntheticFileUri("<unknown>");
        // getPath() decodes the percent-encoded '<'/'>' back.
        assertEquals("/<unknown>", uri.getPath());
        assertTrue(uri.getPath().endsWith("/<unknown>"),
                "the line-map fallback matches on the /<unknown> path suffix");
    }
}
