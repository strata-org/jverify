package org.strata.java.bytecode.testengine;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.strata.jverify.testengine.Blocker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class CortexXfailManifest {

    static final String PROPERTY = "jverify.cortex";
    static final String RESOURCE = "/org/strata/java/bytecode/testengine/cortex-xfails.txt";
    static final String LAUREL_CORTEX_PROPERTY = "jverify.laurelCortex";
    static final String LAUREL_CORTEX_RESOURCE = "/org/strata/java/bytecode/testengine/laurel-cortex-xfails.txt";

    record Xfail(Blocker blocker, String reason) {
    }

    private final Map<String, Xfail> forcedXfail;
    private final Map<String, String> forcedRun;
    private final Map<String, String> forcedSkip;

    private final String resource;

    private CortexXfailManifest(String resource, Map<String, Xfail> forcedXfail, Map<String, String> forcedRun,
                                Map<String, String> forcedSkip) {
        this.resource = resource;
        this.forcedXfail = forcedXfail;
        this.forcedRun = forcedRun;
        this.forcedSkip = forcedSkip;
    }

    @Nullable Xfail forcedXfail(String className) {
        return forcedXfail.get(className);
    }

    boolean isForcedRun(String className) {
        return forcedRun.containsKey(className);
    }

    @Nullable String skipReason(String className) {
        return forcedSkip.get(className);
    }

    String fileName() {
        return resource.substring(resource.lastIndexOf('/') + 1);
    }

    static CortexXfailManifest load() {
        return load(RESOURCE);
    }

    static CortexXfailManifest load(String resource) {
        Map<String, Xfail> xfail = new HashMap<>();
        Map<String, String> run = new HashMap<>();
        Map<String, String> skip = new HashMap<>();
        try (InputStream in = CortexXfailManifest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return new CortexXfailManifest(resource, xfail, run, skip);
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
                    if (parts.length < 2) {
                        throw new IllegalStateException(malformed(resource, lineNo, line, "missing class name"));
                    }
                    String fqcn = parts[1];
                    if (xfail.containsKey(fqcn) || run.containsKey(fqcn) || skip.containsKey(fqcn)) {
                        throw new IllegalStateException(malformed(resource, lineNo, line, "duplicate entry for " + fqcn));
                    }
                    switch (parts[0]) {
                        case "XFAIL" -> {
                            if (parts.length < 3) {
                                throw new IllegalStateException(malformed(resource, lineNo, line,
                                        "XFAIL needs: XFAIL <fqcn> <BLOCKER> <reason>"));
                            }
                            Blocker blocker;
                            try {
                                blocker = Blocker.valueOf(parts[2]);
                            } catch (IllegalArgumentException e) {
                                throw new IllegalStateException(malformed(resource, lineNo, line,
                                        "unknown Blocker '" + parts[2] + "'"));
                            }
                            xfail.put(fqcn, new Xfail(blocker, remainder(parts, 3)));
                        }
                        case "RUN" -> run.put(fqcn, remainder(parts, 2));
                        case "SKIP" -> skip.put(fqcn, remainder(parts, 2));
                        default -> throw new IllegalStateException(malformed(resource, lineNo, line,
                                "line must start with XFAIL, RUN or SKIP"));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + resource, e);
        }
        return new CortexXfailManifest(resource, xfail, run, skip);
    }

    private static String remainder(String[] parts, int from) {
        return String.join(" ", Arrays.copyOfRange(parts, Math.min(from, parts.length), parts.length));
    }

    private static String malformed(String resource, int lineNo, String line, String why) {
        return "Malformed " + resource + " at line " + lineNo + " (" + why + "): " + line;
    }
}
