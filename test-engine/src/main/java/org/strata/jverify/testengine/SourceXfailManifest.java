package org.strata.jverify.testengine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-class xfail overrides for the SOURCE frontend, loaded from the
 * {@code source-xfails.txt} classpath resource -- the counterpart of the bytecode
 * frontend's {@code bytecode-xfails.txt}, in the same format.
 *
 * <p>The verification corpus is shared by both frontends, and a fixture's
 * {@code @JVerifyTest} expectations are what a CORRECT frontend produces. Where only
 * one frontend falls short, that frontend records the exception in its own manifest
 * instead of the fixture being bent to its output. This file holds the source
 * frontend's: fixtures pinned to the bytecode frontend's output because javac
 * desugars a construct this frontend refuses before the bytecode frontend sees it.
 *
 * <ul>
 *   <li>{@code XFAIL fqcn BLOCKER reason} -- run the test but invert its outcome,
 *       exactly as {@code @JVerifyTest(expectedToFail = true)} would. A pass trips
 *       the XPASS wire, demanding the entry be removed.
 *   <li>{@code RUN fqcn reason} -- run the test normally even though the shared
 *       annotation marks it {@code expectedToFail}.
 * </ul>
 */
final class SourceXfailManifest {

    static final String RESOURCE = "/org/strata/jverify/testengine/source-xfails.txt";

    /** A forced-xfail entry: the blocker and human-readable reason. */
    record Xfail(Blocker blocker, String reason) {
    }

    private static volatile SourceXfailManifest instance;

    private final Map<String, Xfail> forcedXfail;
    private final Map<String, String> forcedRun;

    private SourceXfailManifest(Map<String, Xfail> forcedXfail, Map<String, String> forcedRun) {
        this.forcedXfail = forcedXfail;
        this.forcedRun = forcedRun;
    }

    /** The forced-xfail entry for {@code className}, or {@code null}. */
    Xfail forcedXfail(String className) {
        return forcedXfail.get(className);
    }

    /** True when {@code className} should run normally despite an expectedToFail annotation. */
    boolean isForcedRun(String className) {
        return forcedRun.containsKey(className);
    }

    /** The manifest, loaded once from the classpath; empty if the resource is absent. */
    static SourceXfailManifest get() {
        var m = instance;
        if (m == null) {
            synchronized (SourceXfailManifest.class) {
                m = instance;
                if (m == null) {
                    m = load();
                    instance = m;
                }
            }
        }
        return m;
    }

    private static SourceXfailManifest load() {
        Map<String, Xfail> xfail = new HashMap<>();
        Map<String, String> run = new HashMap<>();
        try (InputStream in = SourceXfailManifest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new SourceXfailManifest(xfail, run);
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
                            if (xfail.containsKey(fqcn) || run.containsKey(fqcn)) {
                                throw new IllegalStateException(malformed(lineNo, line,
                                        "duplicate manifest entry for " + fqcn));
                            }
                            xfail.put(fqcn, new Xfail(blocker, remainder(parts, 3)));
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
        return new SourceXfailManifest(xfail, run);
    }

    private static String remainder(String[] parts, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(parts, Math.min(from, parts.length), parts.length));
    }

    private static String malformed(int lineNo, String line, String why) {
        return "Malformed " + RESOURCE + " at line " + lineNo + " (" + why + "): " + line;
    }
}
