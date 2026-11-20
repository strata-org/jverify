package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Range;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a method's statements do to static fields, kept for explaining a
 * failed assertion about one.
 *
 * <p>A static field is a global variable, and after a call the caller knows
 * about the globals the callee may write only what the callee's contract
 * says. A callee without a body has no body to look at; a callee with a body
 * but without a postcondition about the field leaves the caller nothing
 * either; and a use of a class may run its static initializer. So when an
 * assertion that reads a static field fails after such a call, the call is a
 * likely reason, and the diagnostic names it. This is a heuristic over
 * source lines: it can name a call that did not matter, and it never hides
 * the failure itself.
 */
public final class StaticEffectNotes {

    /** Why a call may change a static field without the caller knowing the new value. */
    public enum Kind {
        /** The callee has no body in the program (a library method). */
        NO_BODY,
        /** The callee has a body, but the caller is verified against its contract, not its body. */
        BODY,
        /** A use of a class that may run its static initializer. */
        INITIALIZER
    }

    public record OpaqueCall(int line, String description, Kind kind) {
    }

    private final Map<URI, List<OpaqueCall>> calls = new HashMap<>();
    private final Map<URI, Set<Integer>> staticReadLines = new HashMap<>();

    void recordCall(URI uri, int line, String description, Kind kind) {
        calls.computeIfAbsent(uri, u -> new ArrayList<>()).add(new OpaqueCall(line, description, kind));
    }

    void recordStaticRead(URI uri, int line) {
        staticReadLines.computeIfAbsent(uri, u -> new HashSet<>()).add(line);
    }

    /**
     * The text to append to a failed verification condition at {@code line} of
     * the method spanning {@code method}, or null when nothing applies: the
     * line reads no static field, or no call that may have changed one
     * precedes it in the method.
     *
     * <p>{@code bodilessCallsLoseStatics} says whether the pipeline treats a
     * call to a bodiless procedure as changing every global. The Laurel
     * pipeline treats such a call as changing no global unless the procedure
     * declares otherwise; the Cortex pipeline treats it as changing all of
     * them.
     */
    public String explain(URI uri, Range method, int line, boolean bodilessCallsLoseStatics) {
        var reads = staticReadLines.get(uri);
        if (reads == null || !reads.contains(line)) {
            return null;
        }
        OpaqueCall latest = null;
        for (var call : calls.getOrDefault(uri, List.of())) {
            if (call.line() < method.start().line() || call.line() > line) {
                continue;
            }
            if (call.kind() == Kind.NO_BODY && !bodilessCallsLoseStatics) {
                continue;
            }
            if (latest == null || call.line() >= latest.line()) {
                latest = call;
            }
        }
        return latest == null ? null : "; a static field may have been changed by " + latest.description();
    }

    static String describe(Kind kind, String name) {
        return switch (kind) {
            case NO_BODY -> "the call to '" + name + "', which has no body";
            case BODY -> "the call to '" + name + "', whose body is not visible to the caller";
            case INITIALIZER -> "the static initializer of '" + name + "', which this method may run";
        };
    }
}
