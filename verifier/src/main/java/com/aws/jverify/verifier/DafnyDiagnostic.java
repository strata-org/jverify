package com.aws.jverify.verifier;

import com.aws.jverify.common.Range;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.tools.Diagnostic;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Adapts Dafny's {@code --json-diagnostics} output to the {@link Diagnostic} interface.
 */
public class DafnyDiagnostic implements Diagnostic<String> {
    private static final int SEVERITY_ERROR = 1;
    private static final int SEVERITY_WARNING = 2;
    private static final int SEVERITY_INFO = 4;

    public Location location;

    public int severity;

    public String message;

    /**
     * Corresponds to Dafny's {@code MessageSource} enum,
     * whose values include {@code Parser}, {@code Resolver}, {@code Verifier}, etc.
     */
    @JsonProperty("source")
    public String messageSource;

    public List<RelatedInfo> relatedInformation;

    public Range getRange() {
        return location.range;
    }

    @Override
    public Kind getKind() {
        return switch (severity) {
            case SEVERITY_ERROR -> Kind.ERROR;
            case SEVERITY_WARNING -> Kind.WARNING;
            case SEVERITY_INFO -> Kind.NOTE;
            default -> throw new IllegalStateException("Unexpected severity: " + severity);
        };
    }

    @Override
    public String getSource() {
        return location == null ? null : location.filename();
    }

    @Override
    public long getPosition() {
        return getStartPosition();
    }

    @Override
    public long getStartPosition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getEndPosition() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getLineNumber() {
        return location.range.start().line();
    }

    public long getEndLineNumber() {
        return location.range.end().line();
    }

    @Override
    public long getColumnNumber() {
        return location.range.start().character();
    }

    public long getEndColumnNumber() {
        return location.range.end().character();
    }

    @Override
    public String getCode() {
        return null;
    }

    @Override
    public String getMessage(Locale locale) {
        return message;
    }

    /**
     * Returns a stream containing this diagnostic and its related information as diagnostics.
     */
    public Stream<DafnyDiagnostic> flattenRelated() {
        Stream<RelatedInfo> relatedStream = relatedInformation == null ? Stream.empty() : relatedInformation.stream();
        return Stream.concat(Stream.of(this), relatedStream.map(RelatedInfo::asDiagnostic));
    }

    public record Location(String filename, String uri, Range range) {}

    public record RelatedInfo(Location location, String message) {
        public DafnyDiagnostic asDiagnostic() {
            var diagnostic = new DafnyDiagnostic();
            diagnostic.location = location;
            diagnostic.severity = SEVERITY_INFO;
            diagnostic.message = "Related location: " + message;
            return diagnostic;
        }
    }
}
