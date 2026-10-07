package org.strata.java.bytecode.verifier.laurel;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link StrataBackend#parseDiagnosticLine}'s handling of the
 * synthetic-offset pipeline (CR-303117697), independent of the codec's
 * packing: the offset is parsed as {@code long} and passed to the resolver
 * intact, and a resolver rejection or an unparseable number degrades to a
 * counted malformed-diagnostic rather than crashing the run. The resolver's
 * own decode/URI-validation is covered by {@code PositionResolverTest}.
 */
class DiagnosticPositionTest {

    private static final URI INPUT = URI.create("file:///work/Demo.java");
    private static final URI FALLBACK = URI.create("file:///work/fallback.java");

    /** Records the first offset it is handed, so the test can assert it arrived intact. */
    private static final class CapturingResolver implements PositionResolver {
        long firstOffset = Long.MIN_VALUE;

        @Override
        public Position positionOf(URI file, long offset) {
            if (firstOffset == Long.MIN_VALUE) {
                firstOffset = offset;
            }
            return new Position(1, 1);
        }
    }

    @Test
    void offsetBeyondIntRangeIsParsedAsLongNotCrashing() {
        var resolver = new CapturingResolver();
        long big = 3_000_000_009L;   // > Integer.MAX_VALUE: would crash Integer.parseInt
        var d = StrataBackend.parseDiagnosticLine(
                "file:///work/Demo.java:" + big + "-" + (big + 15) + ": assertion does not hold",
                resolver, FALLBACK);
        assertEquals(big, resolver.firstOffset, "start offset must be parsed as a long, intact");
        assertEquals(INPUT, d.getUri());
        assertFalse(d.getMessage(null).contains("malformed"), d.getMessage(null));
    }

    @Test
    void offsetBeyondLongDegradesToMalformed() {
        var d = StrataBackend.parseDiagnosticLine(
                "file:///work/Demo.java:99999999999999999999-1: message",
                (uri, offset) -> new Position(1, 1), FALLBACK);
        assertEquals(FALLBACK, d.getUri());
        assertTrue(d.getMessage(null).contains("malformed diagnostic"), d.getMessage(null));
    }

    @Test
    void resolverRejectionDegradesToMalformed() {
        // The production resolver throws for a URI that was never an input;
        // parseDiagnosticLine must degrade that to a malformed-diagnostic,
        // not let it propagate and abort the run.
        PositionResolver rejecting = (uri, offset) -> {
            throw new IllegalArgumentException("not a frontend input: " + uri);
        };
        var d = StrataBackend.parseDiagnosticLine(
                "file:///phantom/unknown.java:3000009-3000024: message",
                rejecting, FALLBACK);
        assertEquals(FALLBACK, d.getUri());
        assertTrue(d.getMessage(null).contains("malformed diagnostic"), d.getMessage(null));
        assertTrue(d.getMessage(null).contains("not a frontend input"), d.getMessage(null));
    }

    /** A resolver that knows only {@code known}, as the production one rejects other URIs. */
    private static PositionResolver knowing(URI known) {
        return (uri, offset) -> {
            if (!uri.equals(known)) {
                throw new IllegalArgumentException("not a frontend input: " + uri);
            }
            return new Position(65, 9);
        };
    }

    @Test
    void stringSchemeContractUriResolves() {
        URI contract = URI.create("string:///object-contract.java");
        var d = StrataBackend.parseDiagnosticLine(
                "string:///object-contract.java:3000009-3000024: this proposition could not be proved",
                knowing(contract), FALLBACK);
        assertEquals(contract, d.getUri());
        assertEquals(new Position(65, 9), d.getRange().start());
        assertFalse(d.getMessage(null).contains("malformed"), d.getMessage(null));
        assertEquals("object-contract.java", d.filename());
    }

    @Test
    void jarSchemeContractUriResolvesAndNamesTheEntry() {
        URI contract = URI.create("jar:file:///x.jar!/a/B.java");
        var d = StrataBackend.parseDiagnosticLine(
                "jar:file:///x.jar!/a/B.java:3000009-3000024: message",
                knowing(contract), FALLBACK);
        assertEquals(contract, d.getUri());
        assertFalse(d.getMessage(null).contains("malformed"), d.getMessage(null));
        // An opaque jar: URI has no path; both names fall back to its scheme-specific part.
        assertEquals("B.java", d.filename());
        assertNotNull(d.filePath());
    }
}
