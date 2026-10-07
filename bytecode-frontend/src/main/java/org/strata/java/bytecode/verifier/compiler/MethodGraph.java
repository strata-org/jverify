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
        /**
         * A library contract member (classFqn = its sort or static owner fqn,
         * name = its Laurel name): never diagnosed or counted, and dropped when
         * no surviving program method reaches it.
         */
        final boolean library;
        MethodVerificationStatus status;
        Procedure proc;         // emitted procedure, for the drop pass
        String sort;            // composite sort holding proc (null for statics)
        boolean refused;        // translation threw, or demoted by the fixpoint
        /** Why a library member was refused, chained into its callers' diagnostics; null if unknown. */
        String refusalReason;
        final java.util.Set<String> callees = new java.util.LinkedHashSet<>();
        /**
         * Quiet infos only: callee identifiers of the InstanceCall/StaticCall
         * nodes in the emitted preconditions, postconditions, and throwsOn
         * blocks (the only parts of an opted-out method that are emitted).
         */
        final java.util.Set<String> specCalleeNames = new java.util.LinkedHashSet<>();

        MethodInfo(String classFqn, String name, boolean isStatic, URI uri, Range namePoint,
                   int ordinal, boolean quiet) {
            this(classFqn, name, isStatic, uri, namePoint, ordinal, quiet, false);
        }

        MethodInfo(String classFqn, String name, boolean isStatic, URI uri, Range namePoint,
                   int ordinal, boolean quiet, boolean library) {
            this.classFqn = classFqn;
            this.name = name;
            this.isStatic = isStatic;
            this.uri = uri;
            this.namePoint = namePoint;
            this.ordinal = ordinal;
            this.quiet = quiet;
            this.library = library;
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
     *
     * <p>A refused library member with a {@link MethodInfo#refusalReason}
     * appends it ({@code call to 'X', which could not be translated:
     * <reason>}, with X the Laurel name up to the first {@code '?'}); a
     * demoted library caller records that text as its own reason, so the
     * chain survives. Program-to-program cascades keep the bare form.
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
            // Refused library members by name key, so their reason can be
            // chained into the callers' diagnostics.
            var refusedLibrary = new HashMap<String, MethodInfo>();
            for (var info : methodInfos.values()) {
                if (info.refused && !info.quiet) {
                    refusedNames.add(info.nameKey());
                    if (info.library) {
                        refusedLibrary.put(info.nameKey(), info);
                    }
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
                    String calleeName = calleeKey.substring(calleeKey.indexOf('#') + 1);
                    int mangle = calleeName.indexOf('?');
                    if (mangle >= 0) {
                        calleeName = calleeName.substring(0, mangle);
                    }
                    String reason = "call to '" + calleeName + "', which could not be translated";
                    var libraryCallee = refusedLibrary.get(calleeKey);
                    if (libraryCallee != null && libraryCallee.refusalReason != null) {
                        reason += ": " + libraryCallee.refusalReason;
                    }
                    if (info.library) {
                        info.refusalReason = reason;
                    }
                    reportRefusal(info, reason);
                    changed = true;
                    break;
                }
            }
        }
    }

    /**
     * Mark every library member that no surviving program method reaches as
     * refused (no diagnostic), so the drop pass removes it. Roots are the
     * non-refused program infos: a verified root follows its callee edges; a
     * quiet root (emitted without its implementation) reaches a library
     * member whose instance Laurel name, or static {@code owner_name}, is in
     * its {@link MethodInfo#specCalleeNames}. From there callee edges are
     * followed through non-refused library members. Name matching can only
     * keep a member, never drop a referenced one.
     */
    void pruneUnreachableLibraryMembers() {
        var libraryByKey = new LinkedHashMap<String, MethodInfo>();
        for (var info : methodInfos.values()) {
            if (info.library && !info.refused) {
                libraryByKey.put(info.nameKey(), info);
            }
        }
        if (libraryByKey.isEmpty()) {
            return;
        }
        var work = new java.util.ArrayDeque<String>();
        for (var info : methodInfos.values()) {
            if (info.refused || info.library) {
                continue;
            }
            if (!info.quiet) {
                work.addAll(info.callees);
                continue;
            }
            for (var lib : libraryByKey.values()) {
                String emitted = lib.isStatic ? lib.classFqn + "_" + lib.name : lib.name;
                if (info.specCalleeNames.contains(emitted)) {
                    work.add(lib.nameKey());
                }
            }
        }
        var reached = new java.util.HashSet<String>();
        while (!work.isEmpty()) {
            String key = work.poll();
            var lib = libraryByKey.get(key);
            if (lib == null || !reached.add(key)) {
                continue;
            }
            work.addAll(lib.callees);
        }
        for (var lib : libraryByKey.values()) {
            if (!reached.contains(lib.nameKey())) {
                lib.refused = true;
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
        if (info.library) {
            return; // library members are never diagnosed; their callers carry the reason
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
