package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.java.bytecode.verifier.laurel.StrataDiagnostic;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Collects frontend (translation-phase) diagnostics. Port of JFE's Reporter
 * with the javac diagnostic factory replaced by a plain range-carrying
 * diagnostic.
 *
 * <p>Message texts mirror JFE's {@code messages.properties} entries so the
 * ported test suite matches on identical strings (e.g. {@code
 * translatorError={0}}, {@code wrongContract={0} are not allowed in a {1}}).
 * Frontend diagnostics format javac-style with a lowercase {@code error: }
 * prefix (JFE's Driver.formatMessage for JCDiagnostics), unlike Strata
 * diagnostics which use {@code Error: }.
 */
public class Reporter {
    private final List<Diagnostic<?>> diagnostics = new ArrayList<>();
    private boolean hasErrors = false;

    /** A frontend diagnostic; formats "error: msg" like JFE's javac diagnostics. */
    public static final class FrontendDiagnostic extends StrataDiagnostic {
        public FrontendDiagnostic(URI uri, Range range, String message, int severity) {
            super(uri, range, message, severity);
        }

        @Override
        public String getMessage(Locale locale) {
            String prefix = switch (getKind()) {
                case ERROR -> "error";
                case WARNING -> "warning";
                case MANDATORY_WARNING -> "required warning";
                case NOTE -> "note";
                default -> getKind().toString();
            };
            // Strip StrataDiagnostic's "Error: " style prefix and re-prefix.
            String base = super.getMessage(locale);
            int colon = base.indexOf(": ");
            return prefix + ": " + (colon >= 0 ? base.substring(colon + 2) : base);
        }
    }

    public void reportError(URI uri, Range range, String message) {
        diagnostics.add(new FrontendDiagnostic(uri, range, message, StrataDiagnostic.SEVERITY_ERROR));
        hasErrors = true;
    }

    public void reportWarning(URI uri, Range range, String message) {
        diagnostics.add(new FrontendDiagnostic(uri, range, message, StrataDiagnostic.SEVERITY_WARNING));
    }

    public void reportError(URI uri, Range range, String message, Object... args) {
        reportError(uri, range, format(message, args));
    }

    /** {@code MessageFormat}-lite: only the {n} placeholders JFE's keys use. */
    private static String format(String template, Object... args) {
        String out = template;
        for (int i = 0; i < args.length; i++) {
            out = out.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return out;
    }

    public static final Range NO_RANGE = new Range(new Position(1, 1), new Position(1, 1));

    public List<Diagnostic<?>> diagnostics() {
        return diagnostics;
    }

    public boolean hasErrors() {
        return hasErrors;
    }
}
