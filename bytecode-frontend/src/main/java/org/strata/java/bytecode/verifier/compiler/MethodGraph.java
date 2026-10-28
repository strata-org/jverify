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
 * passes plus the refusal diagnostics (static/instance severity asymmetry
 * and per-(method, reason) dedup).
 */
final class MethodGraph {

    /**
     * Everything the refusal-propagation fixpoint needs about a translated or
     * refused method, keyed by "classFqn#name" (unique because overloads are
     * refused).
     */
    static final class MethodInfo {
        final String classFqn;
        final String name;
        /**
         * The name this method is EMITTED under: {@link #name} plus its overload
         * suffix. Separate from {@code name}, which is what diagnostics quote --
         * the user never wrote the suffix.
         */
        final String laurelName;
        final boolean isStatic;
        final URI uri;
        final Range namePoint;
        /** Distinguishes same-name overload declarations. */
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
        /**
         * Refused but QUIET (opted out), and its callers must still be demoted: a
         * skipped {@code @Transparent} method whose frame is not empty cannot be reduced to
         * a callable declaration (see BytecodeToLaurelCompiler's frame gate), so a
         * call to it has nothing honest to bind to. Quiet refusals are otherwise
         * excluded from the cascade, because a call to an ordinary opted-out method
         * is refused at the call site instead.
         */
        boolean cascadeEvenIfQuiet;
        /**
         * A hard ERROR was already reported at a construct inside this method, so
         * the method-level refusal diagnostic must not also fire -- it would repeat
         * the same defect less precisely. The
         * method is still demoted, so it cannot count Verified.
         */
        boolean constructErrorReported;
        final java.util.Set<String> callees = new java.util.LinkedHashSet<>();
        /**
         * Quiet infos only: callee identifiers of the InstanceCall/StaticCall
         * nodes in the emitted preconditions, postconditions, and throwsOn
         * blocks (the only parts of an opted-out method that are emitted).
         */
        final java.util.Set<String> specCalleeNames = new java.util.LinkedHashSet<>();

        MethodInfo(String classFqn, String name, boolean isStatic, URI uri, Range namePoint,
                   int ordinal, boolean quiet) {
            this(classFqn, name, name, isStatic, uri, namePoint, ordinal, quiet, false);
        }

        MethodInfo(String classFqn, String name, String laurelName, boolean isStatic, URI uri,
                   Range namePoint, int ordinal, boolean quiet) {
            this(classFqn, name, laurelName, isStatic, uri, namePoint, ordinal, quiet, false);
        }

        MethodInfo(String classFqn, String name, String laurelName, boolean isStatic, URI uri,
                   Range namePoint, int ordinal, boolean quiet, boolean library) {
            this.classFqn = classFqn;
            this.name = name;
            this.laurelName = laurelName;
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

        /**
         * By-name identity: what a call edge can actually reference. Keyed on
         * the EMITTED name, so an overload's refusal does not poison calls to
         * its same-named sibling -- the two are distinct Laurel procedures.
         */
        String nameKey() {
            return classFqn + "#" + laurelName;
        }
    }

    final Map<String, MethodInfo> methodInfos = new LinkedHashMap<>();
    /**
     * The reasons a later caller's cascade message may quote: methodKey -> reasons.
     * Recorded: an INSTANCE method's own refusal, and ANY method's cascade
     * demotion -- but not a static method's own refusal, which is reported without
     * being recorded. So
     * {@code call to 'accumulate', which could not be translated: call to 'helper',
     * which could not be translated} chains through a demoted static, while a caller
     * of a directly-refused static gets the bare form (PureSkippedWithFrameIsDropped,
     * CascadeDepth3, TransitiveSkip).
     */
    private final Map<String, java.util.Set<String>> recordedReasons = new HashMap<>();
    /** Diagnostic dedup: methodKey -> reasons reported. */
    private final Map<String, java.util.Set<String>> reportedRefusals = new HashMap<>();

    private final Reporter reporter;

    MethodGraph(Reporter reporter) {
        this.reporter = reporter;
    }

    /**
     * Caller demotion: each round marks every method that calls an
     * already-refused one, reporting {@code call to
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
            // sibling.
            var refusedNames = new java.util.HashSet<String>();
            // Refused library members by name key, so their reason can be
            // chained into the callers' diagnostics.
            var refusedLibrary = new HashMap<String, MethodInfo>();
            for (var info : methodInfos.values()) {
                if (info.refused && (!info.quiet || info.cascadeEvenIfQuiet)) {
                    refusedNames.add(info.nameKey());
                    if (info.library) {
                        refusedLibrary.put(info.nameKey(), info);
                    }
                }
            }
            // The callee's OWN reason, when exactly one was recorded for that
            // name. Without it the caller is told only that some call "could not
            // be translated" and the reason sits on another line, often in another
            // file, so the user has to go hunting for it. Ambiguous when two
            // declarations share a name with different reasons, so those stay bare.
            var reasonByName = new HashMap<String, String>();
            var ambiguousNames = new java.util.HashSet<String>();
            for (var info : methodInfos.values()) {
                if (!info.refused) {
                    continue;
                }
                var reasons = recordedReasons.get(info.key());
                if (reasons == null || reasons.size() != 1) {
                    ambiguousNames.add(info.nameKey());
                    continue;
                }
                String only = reasons.iterator().next();
                String prev = reasonByName.put(info.nameKey(), only);
                if (prev != null && !prev.equals(only)) {
                    ambiguousNames.add(info.nameKey());
                }
            }
            reasonByName.keySet().removeAll(ambiguousNames);
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
                    String because = "call to '"
                            + ClassResolver.demangle(
                                    calleeKey.substring(calleeKey.indexOf('#') + 1))
                            + "', which could not be translated";
                    var libraryCallee = refusedLibrary.get(calleeKey);
                    String calleeReason = libraryCallee != null && libraryCallee.refusalReason != null
                            ? libraryCallee.refusalReason
                            : reasonByName.get(calleeKey);
                    if (calleeReason != null) {
                        because += ": " + calleeReason;
                    }
                    if (info.library) {
                        info.refusalReason = because;
                    }
                    reportRefusal(info, because, true);
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
                String emitted = lib.isStatic ? lib.classFqn + "_" + lib.laurelName : lib.laurelName;
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
     * Drop the emitted procedures of demoted methods (the post-fixpoint
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
     * Report a method's translation refusal with a severity asymmetry: a
     * static method's failure is an error ({@code translatorError={0}}), an
     * instance method's a warning ({@code instanceMethodNotVerified}) --
     * instance support is deliberately partial, so a refusal demotes to an
     * honest Skipped instead of failing the build. Both anchor at the refused
     * method's own name. The
     * diagnostic is deduped per (method, reason); the demotion never is.
     */
    void reportRefusal(MethodInfo info, String reason) {
        reportRefusal(info, reason, false);
    }

    /**
     * As above. {@code cascade} marks a demotion by the fixpoint rather than the
     * method's own translation failure, which matters only for which reasons are
     * RECORDED for a later caller's message (see {@link #recordedReasons}).
     */
    void reportRefusal(MethodInfo info, String reason, boolean cascade) {
        if (info.quiet) {
            return; // opted-out methods demote silently, never diagnose
        }
        if (info.library) {
            return; // library members are never diagnosed; their callers carry the reason
        }
        if (cascade || !info.isStatic) {
            recordedReasons.computeIfAbsent(info.key(), k -> new java.util.LinkedHashSet<>())
                    .add(reason);
        }
        if (info.constructErrorReported) {
            return; // the construct that caused this was already reported, precisely
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
