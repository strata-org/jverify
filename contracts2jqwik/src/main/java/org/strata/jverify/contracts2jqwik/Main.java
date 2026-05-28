package org.strata.jverify.contracts2jqwik;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line entry point for {@code contracts2jqwik}.
 *
 * <p>Usage:</p>
 * <pre>
 *     contracts2jqwik &lt;input.java&gt; [&lt;output.java&gt;]
 * </pre>
 *
 * <p>Reads a Java source file containing methods annotated with
 * {@link org.strata.jverify.AsProperty}, generates a sibling Java source file
 * containing the corresponding jqwik {@code @Property} test methods, and
 * writes it to {@code output.java} (or to stdout if no output path is given).</p>
 *
 * <p>If the input declares a class {@code Foo} the output declares a class
 * named {@code FooGenerated} that {@code extends Foo}, so the generated
 * tests can call the contract methods directly.</p>
 *
 * <p>The output is plain, formatted Java source intended to be reviewed and
 * (optionally) checked into source control alongside the input.</p>
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: contracts2jqwik <input.java> [<output.java>]");
            System.exit(1);
        }

        Path inputPath = Path.of(args[0]);
        if (!Files.isRegularFile(inputPath)) {
            System.err.println("error: not a file: " + inputPath);
            System.exit(1);
        }

        String source = Files.readString(inputPath);
        Contracts2Jqwik.Result result = Contracts2Jqwik.translate(source, inputPath.getFileName().toString());

        for (String warning : result.warnings) {
            System.err.println("warning: " + warning);
        }

        if (result.output == null) {
            // No @AsProperty methods found — nothing to emit.
            System.err.println("note: no @AsProperty methods found in " + inputPath);
            return;
        }

        if (args.length == 2) {
            Path outputPath = Path.of(args[1]);
            if (Files.isDirectory(outputPath)) {
                outputPath = outputPath.resolve(result.suggestedFileName);
            }
            Files.createDirectories(outputPath.toAbsolutePath().getParent());
            Files.writeString(outputPath, result.output);
            System.err.println("wrote " + outputPath);
        } else {
            System.out.print(result.output);
        }
    }
}
