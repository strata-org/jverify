package com.aws.jverify;

import com.aws.jverify.common.TestMarkup;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.VerifierOptions;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

public class TestUtilities {
    public static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

//    public record DafnyResults(int successCount, int errorCount) {}
//    public static void verifyMarkedSourceFile(String inputFileName, DafnyResults dafnyResults) throws IOException {
//        testMarkedSourceVerification(getTestFileContent(inputFileName), dafnyResults);
//    }
//
//    public static String getTestFileContent(String inputFileName) throws IOException {
//        var directory = Path.of("./src/test/java/com/aws/jverify");
//        var filePath = directory.resolve(inputFileName);
//        return Files.readString(filePath);
//    }
//
//    private static void testMarkedSourceVerification(String markedSource, DafnyResults dafnyResults) throws IOException {
//        int expectedExitCode = dafnyResults.errorCount() > 0 ? 4 : 0;
//        var output = testMarkedSource(markedSource, dafnyResults.errorCount(), expectedExitCode);
//        var pluralization = dafnyResults.errorCount() != 1 ? "s" : "";
//        String ending = "Dafny program verifier finished with " +
//                dafnyResults.successCount() + " verified, " +
//                dafnyResults.errorCount() + " error" + pluralization + "\n";
//        assertThat(output, endsWith(ending));
//    }
//
//    /**
//     * Verifies the given source code, asserting that each markup annotation appears in the output,
//     * and that the exit code and number of matched error annotations are as expected.
//     * <p>
//     * NOTE: Errors that appear in the verification output, but which don't appear in the marked-up source code,
//     * are currently silently ignored.
//     */
//    public static String testMarkedSource(String markedSource, int expectedErrorCount, int expectedExitCode) throws IOException {
//        StringWriter writer = new StringWriter();
//        var options = getVerifierOptions();
//        var result = TestMarkup.getPositionsAndAnnotatedRanges(markedSource);
//        var source = result.output();
//        var exitCode = Driver.verifyJavaSource(options, source, writer);
//        var output = canonicalizeNewlines(writer.toString());
//        var errorsFound = 0;
//        for(var range : result.ranges()) {
//            var positionString = "(" + range.range.toString() + ")";
//            String expectation = positionString + ": " + range.annotation;
//            if (range.annotation.startsWith("Error:") || range.annotation.startsWith("error: ")) {
//                errorsFound++;
//            }
//
//            assertThat(output, containsString(expectation));
//        }
//        Assertions.assertEquals(expectedErrorCount, errorsFound,
//                () -> "Mismatched error count in output:\n%s".formatted(output));
//        Assertions.assertEquals(expectedExitCode, exitCode,
//                () -> "Mismatched exit code; output:\n%s".formatted(output));
//        return output;
//    }
//
//    /**
//     * Returns the text with all CRLF sequences replaced with LF.
//     * This prevents erroneous failures of diff-based assertions on Windows platforms.
//     */
//    public static String canonicalizeNewlines(final String text) {
//        return text.replaceAll("\r\n", "\n");
//    }
//
//    public static int run(String inputFileName, boolean fromExamples, Writer writer) throws IOException {
//        var directory = fromExamples
//                ? Path.of("../examples/src/main/java/com/aws/verifier/examples")
//                : Path.of("./src/test/java/com/aws/jverify");
//        var filePath = directory.resolve(inputFileName);
//        var options = getVerifierOptions();
//        return Driver.verifyJavaExample(options, filePath, writer);
//    }
//
//    private static VerifierOptions getVerifierOptions() {
//        var dafnyPath = Path.of("../dafny").toAbsolutePath()
//                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
//        var libraryJar = Path.of("../library/build/libs/library.jar");
//        var prelude = Path.of("./src/main/resources/additional.dfy");
//        return new VerifierOptions(dafnyPath, libraryJar, prelude,
//                //Path.of("../temp.dfy"),
//                null,
//                null, true,
//                new String[] {
//                        "--use-basename-for-filename"
//                        //,"--wait-for-debugger"
//                }
//        );
//    }
    
}
