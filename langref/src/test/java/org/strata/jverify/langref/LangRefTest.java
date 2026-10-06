package org.strata.jverify.langref;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.strata.jverify.verifier.Main;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class LangRefTest {
    private static final String HEADER = "test,expect";

    private record Outcome(String category, String output) {}

    @TestFactory
    Stream<DynamicTest> langRef() throws Exception {
        var root = Path.of(System.getProperty("langref.root"));
        var corpus = root.resolve("corpus");
        var baselineFile = root.resolve("baseline.csv");
        var update = Boolean.parseBoolean(System.getProperty("langref.update", "false"));
        var threads = Integer.getInteger("langref.threads",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        var timeoutSeconds = Integer.getInteger("langref.timeoutSeconds", 30);

        List<Path> tests;
        try (var files = Files.walk(corpus)) {
            tests = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }

        var outcomes = new LinkedHashMap<String, Outcome>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var futures = new LinkedHashMap<String, Future<Outcome>>();
            for (var test : tests) {
                var key = corpus.relativize(test).toString().replace('\\', '/');
                futures.put(key, pool.submit(() -> run(test, entryOf(key))));
            }
            for (var e : futures.entrySet()) {
                outcomes.put(e.getKey(), await(e.getValue(), timeoutSeconds));
            }
        } finally {
            pool.shutdownNow();
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroyForcibly);
        }

        writeResults(root.resolve("build/langref-results.csv"), outcomes);

        if (update) {
            var lines = new ArrayList<String>();
            lines.add(HEADER);
            outcomes.forEach((k, v) -> lines.add(k + "," + v.category()));
            Files.write(baselineFile, lines, StandardCharsets.UTF_8);
            return Stream.empty();
        }

        var baseline = readBaseline(baselineFile);
        var dynamicTests = new ArrayList<DynamicTest>();
        for (var e : outcomes.entrySet()) {
            var expected = baseline.get(e.getKey());
            var actual = e.getValue();
            dynamicTests.add(DynamicTest.dynamicTest(e.getKey(), () -> {
                if (expected == null) {
                    fail("not in baseline.csv (actual: " + actual.category()
                            + "); rerun with -Plangref.update=true\n" + actual.output());
                }
                assertEquals(expected, actual.category(),
                        "outcome changed; if intended, rerun with -Plangref.update=true\n" + actual.output());
            }));
        }
        for (var key : baseline.keySet()) {
            if (!outcomes.containsKey(key)) {
                dynamicTests.add(DynamicTest.dynamicTest(key, () ->
                        fail("in baseline.csv but missing from corpus/; rerun with -Plangref.update=true")));
            }
        }
        return dynamicTests.stream();
    }

    private static String entryOf(String key) {
        var className = key.substring(0, key.length() - ".java".length()).replace('/', '.');
        return className + "#run";
    }

    private static Outcome run(Path test, String entry) {
        var out = new StringWriter();
        var err = new StringWriter();
        var commandLine = Main.commandLine();
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        int exitCode;
        try {
            exitCode = commandLine.execute("--interpret", "--interpreter", "laurel",
                    "--entry", entry, test.toString());
        } catch (RuntimeException e) {
            return new Outcome("error", e.toString());
        }
        var output = out + err.toString();
        return new Outcome(classify(exitCode, output), output);
    }

    private static String classify(int exitCode, String output) {
        if (output.contains("Known limitation:")) {
            return "refused";
        }
        if (output.contains("Strata exited with code")) {
            return "interp_error";
        }
        if (exitCode == 0) {
            return "passed";
        }
        if (exitCode == 4) {
            return "assert_failed";
        }
        if (output.contains("Unsupported") || output.contains("unsupported")
                || output.contains("not supported") || output.contains("not yet supported")) {
            return "unsupported";
        }
        return "error";
    }

    private static Outcome await(Future<Outcome> future, int timeoutSeconds) throws InterruptedException {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new Outcome("timeout", "no result after " + timeoutSeconds + "s");
        } catch (ExecutionException e) {
            return new Outcome("error", e.getCause().toString());
        }
    }

    private static Map<String, String> readBaseline(Path file) throws IOException {
        var result = new TreeMap<String, String>();
        if (!Files.exists(file)) {
            return result;
        }
        for (var line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.equals(HEADER)) {
                continue;
            }
            var comma = line.lastIndexOf(',');
            result.put(line.substring(0, comma), line.substring(comma + 1));
        }
        return result;
    }

    private static void writeResults(Path file, Map<String, Outcome> outcomes) throws IOException {
        Files.createDirectories(file.getParent());
        var lines = new ArrayList<String>();
        lines.add("test,outcome,firstLine");
        outcomes.forEach((k, v) -> {
            var first = v.output().strip().lines().findFirst().orElse("").replace('"', '\'');
            lines.add(k + "," + v.category() + ",\"" + first + "\"");
        });
        Files.write(file, lines, StandardCharsets.UTF_8);
        var log = new StringBuilder();
        outcomes.forEach((k, v) -> log.append("==== ").append(k).append(" [").append(v.category()).append("]\n")
                .append(v.output().strip()).append("\n"));
        Files.writeString(file.resolveSibling("langref-output.log"), log, StandardCharsets.UTF_8);
    }
}
