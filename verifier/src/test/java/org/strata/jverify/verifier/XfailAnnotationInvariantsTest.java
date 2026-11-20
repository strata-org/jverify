package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.strata.jverify.testengine.JVerifyTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Level-1 enforcement for the xfail scheme (see {@link JVerifyTest#expectedToFail()}
 * / {@link org.strata.jverify.testengine.Blocker}): every {@code @JVerifyTest}
 * that is {@code expectedToFail} MUST list at least one {@code blockedBy} bucket,
 * so no expected-failure can be left uncategorised. The {@code blockedBy} values
 * are an enum, so validity (no typos / vague catch-alls) is enforced by the
 * compiler; this test enforces presence.
 *
 * <p>Source-scan based (not reflection) so it does not require loading every test
 * class, mirroring how the bytecode frontend's test engine locates sources. The scan covers
 * <em>every</em> module's {@code src/test/java} under the located root, not just
 * this module's, so an xfail added under any module is still checked.
 */
public class XfailAnnotationInvariantsTest {

    @Test
    public void everyExpectedToFailHasABlocker() throws IOException {
        List<String> offenders = scanTestSources(XfailAnnotationInvariantsTest::isXfailWithoutBlocker);
        assertTrue(offenders.isEmpty(),
                "These @JVerifyTest classes are expectedToFail but have no blockedBy bucket: " + offenders);
    }

    /**
     * An {@code expectedToFail} test must not also carry a non-empty legacy {@code skip=}.
     * The two are mutually exclusive: the test engine
     * ({@code org.strata.java.bytecode.testengine.BytecodeTestEngine}) evaluates the
     * {@code skip=} hard-skip BEFORE the xfail branch, so a test with both would abort
     * as skipped and never reach the XPASS tripwire -- silently disabling the
     * "a fixed feature can no longer stay skipped" guarantee the xfail scheme exists for.
     */
    @Test
    public void noExpectedToFailAlsoCarriesLegacySkip() throws IOException {
        List<String> offenders = scanTestSources(XfailAnnotationInvariantsTest::isXfailWithLegacySkip);
        assertTrue(offenders.isEmpty(),
                "These @JVerifyTest classes set both expectedToFail and a legacy skip= "
                        + "(mutually exclusive; clear skip=): " + offenders);
    }

    /**
     * Walk every module's {@code src/test/java} under the located root and return the
     * relative path of each source containing a {@code @JVerifyTest(...)} whose argument
     * text (strings/comments stripped) matches {@code isOffender}.
     *
     * <p>Locates the root by climbing from {@code user.dir} to the OUTERMOST ancestor
     * that has a {@code settings.gradle.kts}. No {@code Test} task overrides
     * {@code workingDir}, so {@code user.dir} is the running module's dir (e.g.
     * {@code verifier/}); the outermost marker is the multi-module root -- from which
     * the walk covers every module's {@code src/test/java}, not just this one's.
     * Climbing to the outermost (rather than the first) marker means a nested build
     * that ships its own {@code settings.gradle.kts} (e.g. the sibling
     * {@code sample-project/}) cannot cause the scan to root too early and silently
     * skip modules. As a backstop, the scan asserts it saw a plausible number of test
     * sources, so any future root-resolution regression fails loud instead of passing
     * vacuously.
     */
    private static List<String> scanTestSources(java.util.function.Predicate<String> isOffender)
            throws IOException {
        Path repoRoot = null;
        for (Path cursor = Path.of(System.getProperty("user.dir")); cursor != null; cursor = cursor.getParent()) {
            if (Files.exists(cursor.resolve("settings.gradle.kts"))) {
                repoRoot = cursor;  // keep climbing: take the OUTERMOST marker, not the first
            }
        }
        assertNotNull(repoRoot, "could not locate a settings.gradle.kts root from " + System.getProperty("user.dir"));

        List<String> offenders = new ArrayList<>();
        Path root = repoRoot;
        int scanned = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path f : (Iterable<Path>) paths
                    .filter(XfailAnnotationInvariantsTest::isTestSource)::iterator) {
                scanned++;
                String src = Files.readString(f);
                for (String rawArgs : annotationArgs(stripStringsAndComments(src))) {
                    if (isOffender.test(rawArgs)) {
                        offenders.add(root.relativize(f).toString());
                    }
                }
            }
        }
        // Backstop: a degenerate root (e.g. resolving to a nested single-module build)
        // would scan ~1 file and let every xfail invariant pass vacuously. The real
        // multi-module tree has well over 100 test sources, so a floor of 50 catches a
        // mis-resolved root loudly while tolerating legitimate test-count churn.
        assertTrue(scanned > 50,
                "expected to scan the whole multi-module test tree but only saw " + scanned
                        + " test sources under " + root + " -- root resolution likely regressed");
        return offenders;
    }

    /**
     * A comment or string literal inside a NON-xfail annotation must not be read as
     * an xfail marker: {@link #stripStringsAndComments} removes both before the
     * attribute match, so these shapes are not flagged as offenders, while a genuine
     * live {@code expectedToFail = true} with no {@code blockedBy} still is.
     */
    @Test
    public void commentsAndStringsDoNotCountAsXfailMarkers() {
        // string literal carrying the phrase -> NOT an offender
        assertFalse(isXfailWithoutBlocker(
                "skip = \"TODO: change to expectedToFail = true later\""));
        // block comment carrying the phrase -> NOT an offender
        assertFalse(isXfailWithoutBlocker(
                "/* was expectedToFail = true, dropped */ exitCode = 0"));
        // line comment carrying the phrase -> NOT an offender
        assertFalse(isXfailWithoutBlocker(
                "exitCode = 0 // expectedToFail = true once #NNN lands"));
        // genuine live xfail with no blocker -> IS an offender
        assertTrue(isXfailWithoutBlocker("expectedToFail = true, exitCode = 4"));
        // genuine live xfail WITH a blocker -> NOT an offender
        assertFalse(isXfailWithoutBlocker(
                "expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}"));
    }

    /**
     * expectedToFail + a legacy skip= together is an offender; either alone is fine, and
     * the phrase living only in a comment/string must not count.
     */
    @Test
    public void legacySkipAlongsideExpectedToFailIsAnOffender() {
        // both present -> offender
        assertTrue(isXfailWithLegacySkip(
                "expectedToFail = true, skip = \"old reason\", blockedBy = {Blocker.STRATA_OTHER}"));
        // plain hard-skip, no expectedToFail -> allowed
        assertFalse(isXfailWithLegacySkip("skip = \"still flaky\""));
        // expectedToFail only -> allowed
        assertFalse(isXfailWithLegacySkip(
                "expectedToFail = true, blockedBy = {Blocker.STRATA_OTHER}"));
        // skip= living only inside a comment must not count
        assertFalse(isXfailWithLegacySkip(
                "expectedToFail = true /* was skip = \"x\" */, blockedBy = {Blocker.FRONTEND_OTHER}"));
    }

    /**
     * True when the (raw) annotation arg text declares {@code expectedToFail = true}
     * in live code but names no {@code blockedBy} bucket. Strings and comments are
     * stripped first so a note that merely mentions the phrase does not count.
     */
    private static boolean isXfailWithoutBlocker(String rawArgs) {
        String args = stripStringsAndComments(rawArgs);
        boolean xfail = args.matches("(?s).*expectedToFail\\s*=\\s*true.*");
        boolean hasBlocker = args.matches("(?s).*blockedBy\\s*=.*Blocker\\..*");
        return xfail && !hasBlocker;
    }

    /**
     * True when the (raw) annotation arg text declares {@code expectedToFail = true} and
     * also carries a legacy {@code skip = ...} attribute. Strings/comments are stripped
     * first, so a {@code skip="..."} literal reduces to its bare {@code skip =} attribute
     * -- the string content is irrelevant, any explicit skip alongside expectedToFail is
     * an offender (a plain hard-skip with no expectedToFail is still allowed).
     */
    private static boolean isXfailWithLegacySkip(String rawArgs) {
        String args = stripStringsAndComments(rawArgs);
        boolean xfail = args.matches("(?s).*expectedToFail\\s*=\\s*true.*");
        boolean hasSkip = args.matches("(?s).*\\bskip\\s*=.*");
        return xfail && hasSkip;
    }

    /**
     * Remove string/char literals and block/line comments from annotation arg
     * text so the attribute regexes only see live code. Mirrors the
     * string-awareness of {@link #annotationArgs} (which keeps the raw text for
     * paren balancing).
     */
    private static String stripStringsAndComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inStr = false; char strCh = 0;
        boolean inBlockCmt = false, inLineCmt = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inBlockCmt) {
                if (c == '*' && i + 1 < s.length() && s.charAt(i + 1) == '/') { inBlockCmt = false; i++; }
                continue;
            }
            if (inLineCmt) { if (c == '\n') { inLineCmt = false; out.append(c); } continue; }
            if (inStr) {
                if (c == '\\') { i++; continue; }
                if (c == strCh) inStr = false;
                continue;
            }
            if (c == '/' && i + 1 < s.length()) {
                if (s.charAt(i + 1) == '*') { inBlockCmt = true; i++; continue; }
                if (s.charAt(i + 1) == '/') { inLineCmt = true; i++; continue; }
            }
            if (c == '"' || c == '\'') { inStr = true; strCh = c; continue; }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * A {@code .java} file under some module's {@code src/test/java}, excluding
     * generated {@code build/} trees. Path is normalised to {@code /} so the match
     * is platform-independent.
     */
    private static boolean isTestSource(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.endsWith(".java") && s.contains("/src/test/java/") && !s.contains("/build/");
    }

    @Test
    public void parenScannerIsNotFooledByCommentsOrStrings() {
        // An unbalanced paren inside a comment (the ":(" emoticon) must not make the
        // scanner run to EOF and silently drop the annotation — the malformed xfail
        // below has no blockedBy and must still be extracted so the presence check flags it.
        String evader = "@JVerifyTest(expectedToFail = true /* :( */)\nclass Foo {}";
        var args = annotationArgs(stripStringsAndComments(evader));
        assertTrue(args.stream().anyMatch(XfailAnnotationInvariantsTest::looksLikeXfail),
                "a malformed xfail with a comment-embedded paren must not be dropped by the scanner");
        // A commented-out or in-string annotation must NOT be treated as a live one.
        assertTrue(annotationArgs(stripStringsAndComments("// @JVerifyTest(expectedToFail = true)\nclass B {}")).isEmpty(),
                "a commented-out @JVerifyTest must not be scanned as live");
    }

    private static boolean looksLikeXfail(String args) {
        return args.matches("(?s).*expectedToFail\\s*=\\s*true.*");
    }

    /**
     * Extract the argument text of every {@code @JVerifyTest(...)} in a source file by
     * scanning to the balanced closing paren (a naive non-greedy regex truncates at the
     * first {@code )} inside a string/nested braces, which would let a malformed xfail
     * annotation silently evade this check).
     */
    static List<String> annotationArgs(String src) {
        List<String> out = new ArrayList<>();
        int idx = 0;
        String marker = "@JVerifyTest";
        while ((idx = src.indexOf(marker, idx)) >= 0) {
            int open = src.indexOf('(', idx);
            if (open < 0) break;
            int depth = 0; boolean inStr = false; char strCh = 0;
            int i = open;
            for (; i < src.length(); i++) {
                char c = src.charAt(i);
                if (inStr) {
                    if (c == '\\') { i++; continue; }
                    if (c == strCh) inStr = false;
                } else if (c == '"' || c == '\'') { inStr = true; strCh = c; }
                else if (c == '(' || c == '{') depth++;
                else if (c == ')' || c == '}') { depth--; if (depth == 0) break; }
            }
            if (i < src.length()) out.add(src.substring(open + 1, i));
            idx = (i < src.length()) ? i + 1 : src.length();
        }
        return out;
    }
}
