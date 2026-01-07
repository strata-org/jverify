package com.aws.jverify.verifier.dafny;

import com.aws.jverify.common.Range;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.tools.Diagnostic;
import java.net.URI;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Stream;

/**
 * Adapts Dafny's {@code --json-diagnostics} output to the {@link Diagnostic} interface.
 */
public final class DafnyDiagnostic extends DafnyOutput implements Diagnostic<URI>, DiagnosticWithRange {
    private static final int SEVERITY_ERROR = 1;
    private static final int SEVERITY_WARNING = 2;
    private static final int SEVERITY_INFO = 4;
    private static final int SEVERITY_RELATED_LOCATION = 100;

    public Location location;

    public int severity;

    @JsonProperty("errorId")
    public String errorId;

    @JsonProperty("arguments")
    public String[] arguments;
    
    @JsonProperty("defaultFormatMessage")
    public String defaultFormatMessage;

    public String getErrorId() {
        return errorId;
    }

    public String[] getArguments() {
        return arguments;
    }

    public String getDefaultFormatMessage() {
        return defaultFormatMessage;
    }

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
    public URI getSource() {
        return location == null ? null : URI.create(location.uri);
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

    public String getMessage(Locale locale) {
        String formatMessage = getDefaultFormatMessage();
        String message;
        if (getArguments().length > 0) {
            message = MessageFormat.format(safeFormat(formatMessage), (Object[]) getArguments());
        } else {
            message = formatMessage;
        }
        return getSeverityMessage() + ": "+ message;
    }

    private static String safeFormat(String format) {
        // Replace { not followed by digit with {{
        String escaped = format.replaceAll("\\{(?!\\d)", "{{");
        // Replace } not preceded by digit with }}
        escaped = escaped.replaceAll("(?<!\\d)\\}", "}}");
        
        return escaped.replace("'", "''");
    }
    
    public String getSeverityMessage() {
        return switch(severity) {
            case SEVERITY_WARNING -> "Warning";
            case SEVERITY_ERROR -> "Error";
            case SEVERITY_INFO -> "Info";
            case SEVERITY_RELATED_LOCATION -> "Related location";
            default -> throw new RuntimeException(); 
        };
    }

    /**
     * Returns a stream containing this diagnostic and its related information as diagnostics.
     */
    public Stream<DafnyDiagnostic> flattenRelated() {
        Stream<RelatedInfo> relatedStream = relatedInformation == null ? Stream.empty() : relatedInformation.stream();
        return Stream.concat(Stream.of(this), relatedStream.map(RelatedInfo::asDiagnostic));
    }

    public record Location(String filename, String filePath, String uri, Range range) {}

    public record RelatedInfo(Location location, String errorId, String[] arguments, String defaultFormatMessage) {
        public DafnyDiagnostic asDiagnostic() {
            var diagnostic = new DafnyDiagnostic();
            diagnostic.location = location;
            diagnostic.severity = SEVERITY_RELATED_LOCATION;
            diagnostic.errorId = errorId;
            diagnostic.arguments = arguments;
            diagnostic.defaultFormatMessage = defaultFormatMessage;
            return diagnostic;
        }
    }

    @Override
    public String toString() {
        return "DafnyDiagnostic {" +
                "\n  location=" + location +
                "\n  severity=" + severity +
                "\n  message='" + getMessage(Locale.getDefault()) + '\'' +
                "\n  messageSource='" + messageSource + '\'' +
                "\n  relatedInformation=" + relatedInformation +
                "\n}";
    }
}