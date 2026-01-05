package com.aws.jverify.verifier;

import com.aws.jverify.verifier.dafny.DafnyDiagnostic;
import com.aws.jverify.verifier.dafny.DafnyDriver;
import com.aws.jverify.verifier.dafny.JVerifyResults;
import com.aws.jverify.verifier.laurel.LaurelDriver;
import com.sun.tools.javac.util.JCDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public interface Driver {

    default JVerifyResults verifyJavaFile(JavaFileObject javaFile)
            throws IOException {
        return verifyJavaFiles(List.of(javaFile));
    }
    
    VerifierOptions getVerifierOptions();
    
    default int verifyJavaPaths(List<Path> files) throws IOException {
        List<JavaFileObject> readFiles = files.stream().map((Path p) -> {
            try {
                if (getVerifierOptions().verbose()) {
                    getVerifierOptions().outWriter().println("working directory: " + getVerifierOptions().workingDirectory());
                }
                Path normalized = getVerifierOptions().workingDirectory().resolve(p).normalize();
                return new SourceFile(normalized, Files.readString(normalized));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return verifyJavaFilesExit(readFiles);
    }

    default int verifyJavaFilesExit(
            List<JavaFileObject> readFiles
    ) throws IOException {
        var results = verifyJavaFiles(readFiles);
        outputVerificationResults(results, getVerifierOptions().outWriter());
        getVerifierOptions().outWriter().flush();
        return results.exitCode();
    }

    private void outputVerificationResults(JVerifyResults results, Writer outputWriter) throws IOException {

        var pw = new PrintWriter(outputWriter);
        for (var diagnostic : results.diagnostics()) {
            pw.println(formatDiagnostic(getVerifierOptions().showFilepaths(), diagnostic));
        }
        
        var verificationResults = results.verificationResults();
        if (verificationResults != null) {
            pw.println(String.format("Found %s errors", verificationResults.verificationFailedAssertions()));
            pw.println(String.format("Verified methods: %s", verificationResults.verificationPassedMethods()));
            pw.println(String.format("Failed methods: %s", verificationResults.verificationFailedMethods()));
            pw.println(String.format("Skipped methods: %s", verificationResults.verificationSkippedMethods()));
        } else {
            pw.println(String.format("Found %s errors", results.diagnostics().size()));
        }
    }

    private static String formatDiagnostic(boolean filePath, Diagnostic<?> diagnostic) {
        var sb = new StringBuilder();

        if (diagnostic instanceof JCDiagnostic jcDiagnostic) {
            sb.append(jcDiagnostic.getSource().getName());

            var line = diagnostic.getLineNumber();
            var column = diagnostic.getColumnNumber();
            sb.append("(");
            sb.append(line).append(":").append(column);
            sb.append("-");
            sb.append(line).append(":").append(column + 1);
            sb.append("): ");
        } else if (diagnostic instanceof DafnyDiagnostic dafnyDiagnostic) {
            var filePart = filePath ? dafnyDiagnostic.location.filePath() : dafnyDiagnostic.location.filename();
            sb.append(filePart)
                    .append("(")
                    .append(dafnyDiagnostic.getRange())
                    .append("): ");
        } else {
            throw new IllegalArgumentException(
                    "Formatting not implemented for diagnostic type " + diagnostic.getClass().getName());
        }

        sb.append(Driver.formatMessage(diagnostic));
        return sb.toString();
    }


    JVerifyResults verifyJavaFiles(
            List<JavaFileObject> readFiles
    ) throws IOException;
    
    static Driver getDriver(Backend backend, VerifierOptions options) {
        return switch (backend) {
            case Dafny -> new DafnyDriver(options);
            case Laurel -> new LaurelDriver(options);
            default -> throw new RuntimeException("Unsupported backend: " + backend);
        };
    }
    
    static String formatMessage(Diagnostic<?> diagnostic) {
        if (diagnostic instanceof JCDiagnostic) {
            var prefix = switch (diagnostic.getKind()) {
                case ERROR -> "error";
                case WARNING -> "warning";
                case MANDATORY_WARNING -> "required warning";
                case NOTE -> "note";
                default -> diagnostic.getKind();
            };
            return prefix + ": " + diagnostic.getMessage(null);
        }

        return diagnostic.getMessage(null);
    }
}
