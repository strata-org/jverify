package org.strata.java.bytecode.verifier.compiler;

import java.lang.constant.MethodTypeDesc;
import java.net.URI;
import java.util.List;

/**
 * One library method a call can bind to: a member of a loaded contract
 * class, a static method of a contract helper class, or a method of a
 * {@code JVerify} specification interface. Produced by the
 * {@link LibraryContracts} lookups; never built for a program method.
 *
 * @param kind            where the member comes from
 * @param classInternal   internal name of the class that declares the member
 *                        (the contract or helper class, or the spec interface)
 * @param methodName      the classfile method name
 * @param methodDesc      the member's own (unmapped) descriptor, for locating
 *                        the method in {@code classInternal}'s classfile
 * @param sortOrFqn       instance members: the Laurel sort that owns the
 *                        procedure ({@code String}, {@code Map.Entry},
 *                        {@code JVerify.CharJSequence}); statics: the dotted
 *                        fqn the procedure name is prefixed with
 * @param laurelName      the procedure's name part: {@code methodName}, or
 *                        {@code methodName} plus one {@code '?' + erased type}
 *                        per parameter when the class declares more than one
 *                        same-staticness method of that name
 * @param contracteeFqn   dotted fqn of the contractee (contract members), of
 *                        the helper class, or of the spec interface
 * @param isStatic        the member is static
 * @param isPure          the member is {@code @Pure} (spec-interface members
 *                        always are)
 * @param hasCode         the member's classfile method has a Code attribute
 * @param verify          the effective {@code @Verify} value: class
 *                        {@code overrideChildren} wins, then the member's,
 *                        then the class's, else {@code true}
 * @param returnsTypeVar  the member's generic signature returns a type variable
 * @param paramIsTypeVar  per parameter: its generic signature is a type variable
 * @param uri             the contract source the member was compiled from, or
 *                        null when unknown (spec interfaces)
 * @param contractPure    the declaring contract class says
 *                        {@code @Contract(pure = true)}
 */
record ContractMember(Kind kind, String classInternal, String methodName,
                      MethodTypeDesc methodDesc, String sortOrFqn, String laurelName,
                      String contracteeFqn, boolean isStatic, boolean isPure,
                      boolean hasCode, boolean verify, boolean returnsTypeVar,
                      List<Boolean> paramIsTypeVar, URI uri, boolean contractPure) {

    enum Kind { CONTRACT, HELPER, SPEC_INTERFACE }

    ContractMember {
        paramIsTypeVar = List.copyOf(paramIsTypeVar);
    }

    /** The single key for this member's call edges and its {@code MethodInfo}. */
    String calleeKey() {
        return sortOrFqn + "#" + laurelName;
    }
}
