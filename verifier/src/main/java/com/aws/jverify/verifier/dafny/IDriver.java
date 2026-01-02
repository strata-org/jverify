package com.aws.jverify.verifier.dafny;

import com.aws.jverify.verifier.Backend;
import com.aws.jverify.verifier.SourceFile;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.laurel.LaurelDriver;
import com.sun.tools.javac.util.JCDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public interface IDriver {

    default int verifyJavaPaths(List<Path> files, VerifierOptions verifierOptions) throws IOException {
        List<JavaFileObject> readFiles = files.stream().map((Path p) -> {
            try {
                if (verifierOptions.verbose()) {
                    verifierOptions.outWriter().println("working directory: " + verifierOptions.workingDirectory());
                }
                Path normalized = verifierOptions.workingDirectory().resolve(p).normalize();
                return new SourceFile(normalized, Files.readString(normalized));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        return verifyJavaFilesExit(readFiles, verifierOptions);
    }
    
    int verifyJavaFilesExit(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException;

    JVerifyResults verifyJavaFiles(
            List<JavaFileObject> readFiles,
            VerifierOptions verifierOptions
    ) throws IOException;
    
    public static IDriver getDriver(Backend backend) {
        return backend == Backend.Dafny ? new Driver() : new LaurelDriver();
    }

    public static String formatMessage(Diagnostic<?> diagnostic) {
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
