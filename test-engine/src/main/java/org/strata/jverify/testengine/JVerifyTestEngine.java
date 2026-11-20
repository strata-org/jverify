package org.strata.jverify.testengine;

import org.strata.jverify.common.AnnotatedRange;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.SourceFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helpers for resolving a {@code @JVerifyTest}'s contract sources and the
 * expected diagnostics declared in them. The bytecode frontend's test engine
 * ({@code org.strata.java.bytecode.testengine.BytecodeTestEngine}) runs the
 * tests.
 */
public final class JVerifyTestEngine {

    /**
     * Anchor range for a diagnostic reported against a file other than the one
     * under test. Such a diagnostic carries its real range inside its annotation
     * text ({@code path(range) message}), so the range itself is fixed — it only
     * has to be stable and identical on both the expected and actual side.
     */
    private static final Range CROSS_FILE_RANGE = new Range(new Position(1, 1), new Position(1, 2));

    private JVerifyTestEngine() {
    }

    /**
     * Expected diagnostics declared by markup inside the {@code .java} files of
     * the test's {@code contractSourcePath} directories, in the
     * {@code path(range) message} cross-file form. A contract file is named
     * {@code string://<path relative to its directory>}, as the frontend names
     * directory contract sources, so its label is that URI's path.
     */
    public static List<AnnotatedRange> contractFileRanges(SourceFile markedSourceFile, JVerifyTest annotation)
            throws IOException {
        List<AnnotatedRange> ranges = new ArrayList<>();
        for (Path dir : contractSourcePath(markedSourceFile, annotation)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (var walk = Files.walk(dir)) {
                files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            for (Path file : files) {
                var parsed = TestMarkup.getPositionsAndAnnotatedRanges(Files.readString(file));
                var label = URI.create("string://" + dir.relativize(file).toString().replace('\\', '/'))
                        .getPath();
                for (AnnotatedRange range : parsed.ranges()) {
                    ranges.add(new AnnotatedRange(label + "(" + range.range() + ") " + range.annotation(),
                            CROSS_FILE_RANGE));
                }
            }
        }
        return ranges;
    }

    /** The test's {@code contractSourcePath} entries, resolved against the test file's directory. */
    public static List<Path> contractSourcePath(SourceFile markedSourceFile, JVerifyTest annotation) {
        var parent = Path.of(markedSourceFile.toUri()).getParent();
        return Arrays.stream(annotation.contractSourcePath())
                .map(entry -> parent.resolve(entry).normalize())
                .toList();
    }

    /** The builtin contracts when the test uses them, then the test's own {@code contractSourcePath}. */
    public static List<Path> contractSources(SourceFile markedSourceFile, JVerifyTest annotation) {
        var out = new ArrayList<Path>();
        if (annotation.useBuiltinContracts()) {
            out.add(getBuiltinContractsSourceDir());
        }
        out.addAll(contractSourcePath(markedSourceFile, annotation));
        return out;
    }

    public static Path getBuiltinContractsSourceDir() {
        return Path.of("../builtin-contracts/src/main/java").toAbsolutePath();
    }
}
