package com.aws.jverify.verifier.dafny;

import com.aws.jverify.verifier.DafnyDiagnostic;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class DafnyVerificationResults {
    // dummy value to tell when it hasn't been set
    private int exitCode = -999;

    private final List<Diagnostic<?>> jverifyDiagnostics = new ArrayList<>();
    private final List<DafnyOutput> outputs = new ArrayList<>();

    /**
     * Can be null if verification failed before invoking Dafny.
     */
    private @Nullable Integer dafnyVerifiedCount;

    /**
     * Can be null if verification failed before invoking Dafny.
     */
    private @Nullable Integer dafnyErrorCount;

    /**
     * Can be null if verification failed before invoking Dafny.
     */
    private @Nullable String dafnyFinishedMessage;

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public List<DafnyOutput> getOutputs() {
        return outputs;
    }

    public Stream<Diagnostic<?>> getDiagnostics() {
        return Stream.concat(
                jverifyDiagnostics.stream(),
                outputs.stream().filter(DafnyDiagnostic.class::isInstance).map(DafnyDiagnostic.class::cast));
    }

    public List<Diagnostic<?>> getJverifyDiagnostics() {
        return jverifyDiagnostics;
    }

    public @Nullable Integer getDafnyVerifiedCount() {
        return dafnyVerifiedCount;
    }

    public @Nullable Integer getDafnyErrorCount() {
        return dafnyErrorCount;
    }

    public @Nullable String getDafnyFinishedMessage() {
        return dafnyFinishedMessage;
    }

    public void setDafnyFinishedMessage(@Nullable String dafnyFinishedMessage) {
        this.dafnyFinishedMessage = dafnyFinishedMessage;
    }

    public void setDafnyVerifiedCount(@Nullable Integer dafnyVerifiedCount) {
        this.dafnyVerifiedCount = dafnyVerifiedCount;
    }

    public void setDafnyErrorCount(@Nullable Integer dafnyErrorCount) {
        this.dafnyErrorCount = dafnyErrorCount;
    }

}
