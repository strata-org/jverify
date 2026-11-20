package org.strata.java.bytecode.verifier.laurel;

import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.DiagnosticWithRange;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.net.URI;
import java.util.Locale;

/**
 * A diagnostic parsed from Strata's output.
 */
public class StrataDiagnostic implements Diagnostic<JavaFileObject>, DiagnosticWithRange {
    public static final int SEVERITY_ERROR = 1;
    public static final int SEVERITY_WARNING = 2;
    public static final int SEVERITY_INFO = 4;

    private final URI uri;
    private final Range range;
    private final String message;
    private final int severity;

    public StrataDiagnostic(URI uri, Range range, String message) {
        this(uri, range, message, SEVERITY_ERROR);
    }

    public StrataDiagnostic(URI uri, Range range, String message, int severity) {
        this.uri = uri;
        this.range = range;
        this.message = message;
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** The message without the severity prefix {@link #getMessage(Locale)} adds. */
    public String message() {
        return message;
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public Range getRange() {
        return range;
    }

    @Override
    public String filePath() {
        return pathOf(getUri());
    }

    @Override
    public String filename() {
        // Avoid Paths.get here: the diagnostic URI may be a synthetic path
        // that is not legal on every platform. URI paths always use '/'.
        String path = pathOf(uri);
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * The URI's path, or its scheme-specific part for an opaque URI such as
     * {@code jar:file:///x.jar!/a/B.java}, whose path is null.
     */
    private static String pathOf(URI uri) {
        String path = uri.getPath();
        return path != null ? path : uri.getSchemeSpecificPart();
    }

    @Override
    public Kind getKind() {
        return switch (severity) {
            case SEVERITY_ERROR -> Kind.ERROR;
            case SEVERITY_WARNING -> Kind.WARNING;
            case SEVERITY_INFO -> Kind.NOTE;
            default -> Kind.ERROR;
        };
    }

    @Override
    public JavaFileObject getSource() {
        return null;
    }

    @Override
    public long getPosition() {
        return NOPOS;
    }

    @Override
    public long getStartPosition() {
        return NOPOS;
    }

    @Override
    public long getEndPosition() {
        return NOPOS;
    }

    @Override
    public long getLineNumber() {
        return range.start().line();
    }

    @Override
    public long getColumnNumber() {
        return range.start().character();
    }

    @Override
    public String getCode() {
        return "strata.verification.error";
    }

    @Override
    public String getMessage(Locale locale) {
        return getSeverityMessage() + ": " + message;
    }

    private String getSeverityMessage() {
        return switch (severity) {
            case SEVERITY_WARNING -> "Warning";
            case SEVERITY_ERROR -> "Error";
            case SEVERITY_INFO -> "Info";
            default -> "Error";
        };
    }
}
