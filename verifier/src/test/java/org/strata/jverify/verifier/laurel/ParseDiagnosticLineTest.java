package org.strata.jverify.verifier.laurel;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;

import java.net.URI;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every non-blank line in Strata's diagnostics section is a diagnostic:
 * {@link LaurelDriver#parseDiagnosticLine} must never drop one. A line that
 * does not match the {@code path:start-end: message} shape (for example an
 * empty file path when a source range was lost upstream) previously vanished,
 * turning a failing verification into a vacuous "Found 0 errors".
 */
class ParseDiagnosticLineTest {

    /** Offsets map 1:1 to columns on line 1 — enough to observe the mapping. */
    private static final FilesMap IDENTITY_FILES_MAP =
            (URI file, int offset) -> new Position(1, offset + 1);

    private static final URI FALLBACK = Paths.get("strata").toUri();

    @Test
    void wellFormedLineParsesPathRangeAndMessage() {
        var d = LaurelDriver.parseDiagnosticLine(
                "file:///work/Abs.java:110-113: postcondition does not hold",
                IDENTITY_FILES_MAP, FALLBACK);
        assertEquals(URI.create("file:///work/Abs.java"), d.getUri());
        assertEquals(111, d.getRange().start().character());
        assertEquals(114, d.getRange().end().character());
        assertTrue(d.getMessage(null).contains("postcondition does not hold"));
    }

    @Test
    void blankLineIsIgnored() {
        assertNull(LaurelDriver.parseDiagnosticLine("", IDENTITY_FILES_MAP, FALLBACK));
        assertNull(LaurelDriver.parseDiagnosticLine("   ", IDENTITY_FILES_MAP, FALLBACK));
    }

    @Test
    void malformedLineSurfacesAsErrorInsteadOfVanishing() {
        // The shape produced when a diagnostic's source range is lost
        // upstream: an empty file path. This must be reported, not dropped.
        var d = LaurelDriver.parseDiagnosticLine(
                ":0-0: postcondition does not hold", IDENTITY_FILES_MAP, FALLBACK);
        assertEquals(FALLBACK, d.getUri());
        assertTrue(d.getMessage(null).contains("malformed diagnostic from Strata"));
        assertTrue(d.getMessage(null).contains("postcondition does not hold"),
                "the original line must be preserved in the message");
    }

    /** Mirrors JavaToLaurelCompiler's line-map lookup, which throws for URIs
     *  it has never seen. */
    private static final FilesMap THROWING_FILES_MAP = (URI file, int offset) -> {
        throw new RuntimeException("Could not find line map for " + file);
    };

    @Test
    void lineMapMissDegradesToMalformedDiagnosticInsteadOfCrashing() {
        // A well-shaped line whose URI is not in the line map: location
        // resolution throws. That must become one counted diagnostic, not an
        // exception escaping runVerifier and killing the whole run.
        var d = LaurelDriver.parseDiagnosticLine(
                "file:///not/compiled/Other.java:5-9: assertion does not hold",
                THROWING_FILES_MAP, FALLBACK);
        assertEquals(FALLBACK, d.getUri());
        assertTrue(d.getMessage(null).contains("malformed diagnostic from Strata"));
        assertTrue(d.getMessage(null).contains("assertion does not hold"),
                "the original line must be preserved in the message");
    }

    @Test
    void unparsableUriDegradesToMalformedDiagnosticInsteadOfCrashing() {
        // URI.create throws for a file: path with a space; same guarantee.
        var d = LaurelDriver.parseDiagnosticLine(
                "file:///dir with space/A.java:5-9: assertion does not hold",
                IDENTITY_FILES_MAP, FALLBACK);
        assertEquals(FALLBACK, d.getUri());
        assertTrue(d.getMessage(null).contains("malformed diagnostic from Strata"));
    }
}
