package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Range;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates the compilation's @Contract classes against their contractees:
 * every contract must have a resolvable, non-concrete target, be the target's
 * only contract, carry no other annotations, match the target's type
 * parameters, and declare only methods that exist on the contractee without
 * an internally defined contract. Violations are reported, never thrown.
 */
final class ContractValidator {

    private final Reporter reporter;
    private final SourceMap src;
    private final Declarations decls;
    private final ClassResolver resolver;

    ContractValidator(Reporter reporter, SourceMap src, Declarations decls, ClassResolver resolver) {
        this.reporter = reporter;
        this.src = src;
        this.decls = decls;
        this.resolver = resolver;
    }

    void validate() {
        // Deterministic order without source text: by file URI, then by the
        // contract class's fully-qualified name. (JFE used source order; the
        // frontend has no source, and only which of two duplicate contracts is
        // flagged depends on order -- the diagnostic count is the same.)
        decls.contractDecls.sort(java.util.Comparator
                .comparing((Declarations.ContractDecl e) -> String.valueOf(e.uri()))
                .thenComparing(Declarations.ContractDecl::fqn));
        var seenTargets = new HashMap<String, String>();
        for (var entry : decls.contractDecls) {
            Declarations.TypeNode decl = entry.decl();
            URI uri = entry.uri();
            src.uri = uri;
            // Class-level @Contract diagnostics anchor at the contract class's
            // declaration line, approximated by the earliest member line
            // (MethodBodyFactory.classDeclLine); the class keyword's own line
            // does not survive into the classfile.
            Range classAnchor = src.linePoint(
                    org.strata.java.bytecode.frontend.MethodBodyFactory.classDeclLine(decl.cm()));

            String targetFqn = Declarations.explicitContractTarget(decl.cm());
            if (targetFqn == null) {
                var supers = Declarations.declaredSuperFqns(decl.cm());
                if (!supers.isEmpty()) {
                    targetFqn = supers.get(0);
                }
            }
            if (targetFqn == null) {
                reporter.reportError(uri, classAnchor,
                        "could not find a target for @Contract class '" + decl.name() + "'");
                continue;
            }
            String targetSimple = JavaNames.simpleName(targetFqn);

            String previous = seenTargets.putIfAbsent(targetFqn, entry.fqn());
            if (previous != null) {
                reporter.reportError(uri, classAnchor,
                        "class '" + targetSimple + "' must not be referenced by more than one @Contract annotation");
            }

            var targetDecl = decls.programTypes.get(targetFqn);
            if (targetDecl != null && Declarations.isConcreteClass(targetDecl.cm())) {
                reporter.reportError(uri, classAnchor,
                        "class '" + targetSimple + "' must not have an externally defined contract"
                                + " because all its contracts can be defined internally");
            }

            for (var annotationName : Declarations.annotationNamesOf(decl.cm())) {
                if (!annotationName.equals("Contract")) {
                    reporter.reportError(uri, classAnchor,
                            "annotation '" + annotationName + "' on @Contract class '"
                                    + decl.name() + "' is not allowed, because it must be placed on the contractee");
                }
            }

            if (targetDecl != null) {
                var contractParams = Declarations.typeParamNamesOf(decl.cm());
                var targetParams = Declarations.typeParamNamesOf(targetDecl.cm());
                if (!contractParams.equals(targetParams)) {
                    reporter.reportError(uri, classAnchor,
                            "Contract class '" + decl.name() + "' has different type parameters than the contractee '"
                                    + targetSimple + "'. The parameters must have the same names");
                }
            }

            validateContractMethods(decl, targetFqn, uri);
        }
        src.uri = null;
    }

    /** name+arity signatures of the contractee's methods (with supers), or null when unknowable. */
    private Set<String> contracteeMethodSignatures(String targetFqn) {
        var out = new HashSet<String>();
        String current = targetFqn;
        while (current != null && !current.equals("java.lang.Object")) {
            var srcDecl = decls.programTypes.get(current);
            if (srcDecl != null) {
                for (var m : Declarations.declaredMethodsOf(srcDecl.cm())) {
                    out.add(m.methodName().stringValue() + "/"
                            + m.methodTypeSymbol().parameterCount());
                }
                var supers = Declarations.declaredSuperFqns(srcDecl.cm());
                for (int i = 1; i < supers.size(); i++) {
                    var extra = contracteeMethodSignatures(supers.get(i));
                    if (extra != null) {
                        out.addAll(extra);
                    }
                }
                current = supers.isEmpty() ? null : supers.get(0);
                continue;
            }
            var cm = resolver.loadClassModel(current);
            if (cm == null) {
                return null; // unknowable contractee (e.g. a JDK type): skip checks
            }
            for (var m : cm.methods()) {
                String name = m.methodName().stringValue();
                if (name.equals("<init>") || name.equals("<clinit>")) {
                    continue;
                }
                out.add(name + "/" + m.methodTypeSymbol().parameterCount());
            }
            current = cm.superclass()
                    .map(sc -> JavaNames.dottedFqn(sc.asInternalName()))
                    .orElse(null);
        }
        return out;
    }

    private void validateContractMethods(Declarations.TypeNode decl, String targetFqn, URI uri) {
        var signatures = contracteeMethodSignatures(targetFqn);
        if (signatures == null) {
            return;
        }
        var methods = Declarations.declaredMethodsOf(decl.cm());
        for (var method : methods) {
            String name = method.methodName().stringValue();
            String key = name + "/" + method.methodTypeSymbol().parameterCount();
            Range namePoint = src.pointOf(src.methodCrtRange(method));
            if (!signatures.contains(key)) {
                reporter.reportError(uri, namePoint,
                        "method '" + name + "' is part of a @Contract class,"
                                + " but its signature does not match any method from the contractee");
                continue;
            }
            var targetDecl = decls.programTypes.get(targetFqn);
            if (targetDecl != null) {
                for (var targetMethod : Declarations.declaredMethodsOf(targetDecl.cm())) {
                    if (targetMethod.methodName().stringValue().equals(name)
                            && targetMethod.methodTypeSymbol().parameterCount()
                                    == method.methodTypeSymbol().parameterCount()
                            && Declarations.hasContractHeader(targetMethod)
                            && Declarations.hasContractHeader(method)) {
                        reporter.reportError(uri, namePoint,
                                "method '" + name + "' already has an internally defined contract");
                    }
                }
            }
        }
    }
}
