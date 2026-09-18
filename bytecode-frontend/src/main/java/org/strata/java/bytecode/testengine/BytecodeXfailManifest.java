package org.strata.java.bytecode.testengine;

import org.strata.jverify.testengine.Blocker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-class xfail overrides for the bytecode frontend, loaded from the
 * {@code bytecode-xfails.txt} classpath resource.
 *
 * <p>The verification corpus is owned by StrataJavaFrontEnd (JFE) and shared
 * verbatim; its {@code @JVerifyTest} annotations describe the SOURCE
 * frontend's expected outcomes. This manifest lets StrataJVMFrontEnd override
 * the xfail decision for the BYTECODE frontend without editing any shared
 * fixture, keeping the corpus a single source of truth.
 *
 * <p>Two kinds of entry:
 * <ul>
 *   <li>{@code XFAIL fqcn BLOCKER reason} -- run the test but invert its
 *       outcome (a known bytecode-frontend gap the shared annotation does not
 *       flag), exactly as {@code @JVerifyTest(expectedToFail = true)} would.
 *       A pass trips the XPASS wire, demanding the entry be removed.
 *   <li>{@code RUN fqcn reason} -- run the test normally even though the
 *       shared annotation marks it {@code expectedToFail} (the source gap it
 *       records does not apply to the bytecode pipeline).
 * </ul>
 *
 * @see JVerifyTestEngine#verifyFile
 */
final class BytecodeXfailManifest {

    static final String RESOURCE = "/org/strata/java/bytecode/testengine/bytecode-xfails.txt";

    /** A forced-xfail entry: the blocker and human-readable reason. */
    record Xfail(Blocker blocker, String reason) {
    }

    private final Map<String, Xfail> forcedXfail;
    private final Map<String, String> forcedRun;

    private BytecodeXfailManifest(Map<String, Xfail> forcedXfail, Map<String, String> forcedRun) {
        this.forcedXfail = forcedXfail;
        this.forcedRun = forcedRun;
    }

    /** True when {@code className} should be treated as an xfail on bytecode. */
    boolean isForcedXfail(String className) {
        return forcedXfail.containsKey(className);
    }

    /** The forced-xfail entry for {@code className}, or {@code null}. */
    Xfail forcedXfail(String className) {
        return forcedXfail.get(className);
    }

    /** True when {@code className} should run normally despite an expectedToFail annotation. */
    boolean isForcedRun(String className) {
        return forcedRun.containsKey(className);
    }

    /** Load the manifest from the classpath, or an empty manifest if absent. */
    static BytecodeXfailManifest load() {
        Map<String, Xfail> xfail = new HashMap<>();
        Map<String, String> run = new HashMap<>();
        try (InputStream in = BytecodeXfailManifest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new BytecodeXfailManifest(xfail, run);
            }
            try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] parts = trimmed.split("\\s+");
                    switch (parts[0]) {
                        case "XFAIL" -> {
                            if (parts.length < 3) {
                                throw new IllegalStateException(malformed(lineNo, line,
                                        "XFAIL needs: XFAIL <fqcn> <BLOCKER> <reason>"));
                            }
                            String fqcn = parts[1];
                            Blocker blocker;
                            try {
                                blocker = Blocker.valueOf(parts[2]);
                            } catch (IllegalArgumentException e) {
                                throw new IllegalStateException(malformed(lineNo, line,
                                        "unknown Blocker '" + parts[2] + "'"));
                            }
                            String reason = remainder(parts, 3);
                            if (xfail.containsKey(fqcn) || run.containsKey(fqcn)) {
                                throw new IllegalStateException(malformed(lineNo, line,
                                        "duplicate manifest entry for " + fqcn));
                            }
                            xfail.put(fqcn, new Xfail(blocker, reason));
                        }
                        case "RUN" -> {
                            if (parts.length < 2) {
                                throw new IllegalStateException(malformed(lineNo, line,
                                        "RUN needs: RUN <fqcn> <reason>"));
                            }
                            String fqcn = parts[1];
                            if (xfail.containsKey(fqcn) || run.containsKey(fqcn)) {
                                throw new IllegalStateException(malformed(lineNo, line,
                                        "duplicate manifest entry for " + fqcn));
                            }
                            run.put(fqcn, remainder(parts, 2));
                        }
                        default -> throw new IllegalStateException(malformed(lineNo, line,
                                "line must start with XFAIL or RUN"));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + RESOURCE, e);
        }
        return new BytecodeXfailManifest(xfail, run);
    }

    private static String remainder(String[] parts, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(parts, Math.min(from, parts.length), parts.length));
    }

    private static String malformed(int lineNo, String line, String why) {
        return "Malformed " + RESOURCE + " at line " + lineNo + " (" + why + "): " + line;
    }
}
