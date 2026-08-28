package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Range;
import org.strata.jverify.laurel.Procedure;
import org.strata.java.bytecode.verifier.MethodVerificationStatus;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-method accounting for the refusal-propagation fixpoint: every
 * translated or refused method, its callee edges, and the demotion/drop
 * passes plus the refusal diagnostics (with JFE's severity asymmetry and
 * per-(method, reason) dedup).
 *
 * <p>Extracted verbatim from {@code BytecodeToLaurelCompiler}.
 */
final class MethodGraph {

    /**
     * Everything the refusal-propagation fixpoint needs about a translated or
     * refused method (JFE keys this on MethodSymbols; here the key is
     * "classFqn#name", unique because overloads are refused).
     */
    static final class MethodInfo {
        final String classFqn;
        final String name;
        final boolean isStatic;
        final URI uri;
        final Range namePoint;
        /** Distinguishes same-name overload declarations (JFE keys on MethodSymbol identity). */
        final int ordinal;
        /** Opted out of verification: refusals demote silently, never diagnose. */
        final boolean quiet;
        MethodVerificationStatus status;
        Procedure proc;         // emitted procedure, for the drop pass
        String sort;            // composite sort holding proc (null for statics)
        boolean refused;        // translation threw, or demoted by the fixpoint
        final java.util.Set<String> callees = new java.util.LinkedHashSet<>();

        MethodInfo(String classFqn, String name, boolean isStatic, URI uri, Range namePoint,
                   int ordinal, boolean quiet) {
            this.classFqn = classFqn;
            this.name = name;
            this.isStatic = isStatic;
            this.uri = uri;
            this.namePoint = namePoint;
            this.ordinal = ordinal;
            this.quiet = quiet;
        }

        /** Per-declaration identity (dedup key). */
        String key() {
            return classFqn + "#" + name + "@" + ordinal;
        }

        /** By-name identity: what a call edge can actually reference. */
        String nameKey() {
            return classFqn + "#" + name;
        }
    }

    final Map<String, MethodInfo> methodInfos = new LinkedHashMap<>();
    /** Diagnostic dedup (JFE's reportedInstanceRefusals): methodKey -> reasons reported. */
    private final Map<String, java.util.Set<String>> reportedRefusals = new HashMap<>();

    private final Reporter reporter;

    MethodGraph(Reporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Port of JFE's demoteCallersOfRefusedMethods: one round marks every
     * method that calls an already-refused one, reporting {@code call to
     * 'X', which could not be translated} (severity by the CALLER's own
     * staticness, anchored at the caller's declaration name, deduped per
     * (caller, reason) -- the demotion itself is never deduped). Iterated to
     * a fixpoint: A -> B -> C with C refused demotes B in round one and A in
     * round two. Terminates because the refused set only grows and is
     * bounded by the method count.
     */
    void demoteCallersOfRefusedMethods() {
        boolean changed = true;
        while (changed) {
            changed = false;
            // A call edge is by name (that is all a Laurel call carries), so
            // a callee name is refused when ANY declaration of it is. Quiet
            // (opted-out) refusals are excluded: calls to opted-out methods
            // are refused at the call site by the arity-aware check, and an
            // opted-out overload must not poison calls to its emitted
            // sibling (JFE keys demotion on symbols, not names).
            var refusedNames = new java.util.HashSet<String>();
            for (var info : methodInfos.values()) {
                if (info.refused && !info.quiet) {
                    refusedNames.add(info.nameKey());
                }
            }
            for (var info : methodInfos.values()) {
                if (info.refused) {
                    continue;
                }
                for (String calleeKey : info.callees) {
                    if (!refusedNames.contains(calleeKey)) {
                        continue;
                    }
                    info.refused = true;
                    if (info.status != null) {
                        info.status.setVerificationStatus(
                                MethodVerificationStatus.VerificationStatus.Skipped);
                    }
                    reportRefusal(info, "call to '"
                            + calleeKey.substring(calleeKey.indexOf('#') + 1)
                            + "', which could not be translated");
                    changed = true;
                    break;
                }
            }
        }
    }

    /**
     * Drop the emitted procedures of demoted methods (JFE's post-fixpoint
     * drop pass): their bodies reference unemitted callees, and Strata would
     * fail resolution on them.
     */
    void dropRefusedProcedures(List<Procedure> procedures, CompositeModel composites) {
        for (var info : methodInfos.values()) {
            if (!info.refused || info.proc == null) {
                continue;
            }
            if (info.sort == null) {
                procedures.remove(info.proc);
            } else {
                var list = composites.instanceProcsBySort.get(info.sort);
                if (list != null) {
                    list.remove(info.proc);
                }
            }
            info.proc = null;
        }
    }

    /**
     * Report a method's translation refusal with JFE's severity asymmetry: a
     * static method's failure is an error ({@code translatorError={0}}), an
     * instance method's a warning ({@code instanceMethodNotVerified}) --
     * instance support is deliberately partial, so a refusal demotes to an
     * honest Skipped instead of failing the build. Both anchor at the refused
     * method's own name (JFE's JCMethodDecl preferred position). The
     * diagnostic is deduped per (method, reason); the demotion never is.
     */
    void reportRefusal(MethodInfo info, String reason) {
        if (info.quiet) {
            return; // opted-out methods demote silently (JFE never translates them loudly)
        }
        if (!reportedRefusals.computeIfAbsent(info.key(), k -> new java.util.LinkedHashSet<>())
                .add(reason)) {
            return;
        }
        if (info.isStatic) {
            reporter.reportError(info.uri, info.namePoint, reason);
        } else {
            reporter.reportWarning(info.uri, info.namePoint,
                    "instance method '" + info.name + "' was not verified: " + reason);
        }
    }
}
