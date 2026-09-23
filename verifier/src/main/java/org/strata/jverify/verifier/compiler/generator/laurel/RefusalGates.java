package org.strata.jverify.verifier.compiler.generator.laurel;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.strata.jverify.verifier.compiler.JavaViolationException;

/**
 * The refusal POLICY for instance-method translation, separated from the translation
 * itself: each gate decides whether a call is expressible under Laurel's static
 * resolution, so the method can be demoted rather than emitted unsoundly.
 *
 * <p>All FOUR instance-call gates live here: receiver shape, polymorphic dispatch, and
 * the two overload gates. They are not independent -- receiver-shape refusals are
 * reported before the dispatch refusal so the more specific cause wins -- and the call
 * site states that order explicitly.
 *
 * <p>Each gate REPORTS a reason rather than throwing, so the policy is a set of pure
 * functions over javac symbols that can be unit-tested without running the translator.
 * The caller turns a reported reason into the {@link JavaViolationException} that demotes
 * the method, so the observable behaviour -- same exception, same message -- is unchanged.
 * This matters because refusals are decided per javac SHAPE, and the shapes a fixture suite
 * happens to contain are not the shapes that exist; enumerating them cheaply needs the
 * predicate reachable on its own.
 *
 * <p>Split out of {@code JavaToLaurelCompiler} because the two jobs are independent:
 * translation says how a construct is emitted, this says whether it may be emitted at
 * all. The gates need only javac's type utilities, the opted-out set, and the receiver's
 * enclosing class, so nothing about emission leaks in.
 */
final class RefusalGates {

    private final Types javacTypes;
    private final Set<Symbol> optedOutMethods;

    RefusalGates(Types javacTypes, Set<Symbol> optedOutMethods) {
        this.javacTypes = javacTypes;
        this.optedOutMethods = optedOutMethods;
    }

    /**
     * Refuse overloaded methods. A procedure name carries no signature -- a
     * static mangles to {@code Class?name}, an instance procedure is named by
     * the bare method name inside its composite -- so every overload of a name
     * collapses onto one Laurel procedure. Unrefused, that is a "Duplicate
     * definition" at emission, or worse a call bound to whichever overload was
     * registered last.
     *
     * <p>Counted over the source-declared methods of that name in the enclosing
     * class. Excluded: synthetic members (record accessors, canonical
     * constructors) and methods opted out via {@code @Verify(false)}. They emit
     * nothing, so they cannot collide, and counting them would silently disable
     * verification of the sibling the user did want verified.
     */
    Optional<String> overloadRefusal(Symbol.MethodSymbol sym) {
        int sameName = 0;
        for (Symbol member : JavaToLaurelCompiler.enclosingClassOrRefuse(sym).members().getSymbolsByName(sym.name)) {
            if (member instanceof Symbol.MethodSymbol
                    && (member.flags() & Flags.SYNTHETIC) == 0
                    && !optedOutMethods.contains(member)) {
                sameName++;
            }
        }
        if (sameName > 1) {
            return Optional.of(
                    "overloaded method '" + sym.name + "' (enclosing class declares "
                            + sameName + " methods with this name); overloading is not supported");
        }
        return Optional.empty();
    }

    /**
     * Refuse an instance call whose method NAME denotes more than one signature
     * across the receiver's supertype closure. {@link #overloadRefusal} counts
     * within the IMMEDIATE declaring class and so misses an overload set spanning
     * an inheritance chain -- a subclass declaring an overload (different
     * signature, not an override) of an inherited method: each class declares one,
     * so neither is refused.
     *
     * <p>Unsound without this check: {@code obj#m(args)} drops the declaring
     * class and signature javac selected, and Strata re-resolves {@code #m} BY
     * NAME to the most-specific DECLARER in the receiver's ancestry. With an
     * overload set spanning the chain those choices can differ, and the call
     * verifies against the wrong contract -- silently, since the argument still
     * type-checks whenever the wrongly-selected overload's parameter is a
     * supertype of the right one's.
     *
     * <p>Keyed on distinct SIGNATURES, not declaration count, so a plain override
     * -- same signature in two classes, one contract to bind -- still verifies;
     * overLOADing, not overRIDing, makes by-name resolution ambiguous. Compared
     * after ERASURE: erasure determines javac's applicable set and Strata's
     * composites are erased too, so signatures differing only in type arguments
     * cannot be told apart downstream. A {@code private} method of a STRICT
     * supertype is skipped: not a member of the receiver's type (JLS 8.2), so
     * neither javac nor {@code #m} can select it through such a receiver, and
     * counting it would refuse a call with only one reachable candidate.
     *
     * <p>Refuses rather than assumes wherever the closure cannot be bounded: a
     * non-class receiver type, or a supertype whose class file will not load.
     */
    Optional<String> ancestryOverloadRefusal(Symbol.MethodSymbol sym,
            JCTree.JCExpression methodSelect, Symbol.ClassSymbol currentEnclosingClass) {
        Symbol.ClassSymbol receiver = methodSelect instanceof JCTree.JCFieldAccess fa
                && fa.selected.type != null
                && fa.selected.type.tsym instanceof Symbol.ClassSymbol cls
                        ? cls : currentEnclosingClass;
        if (receiver == null) {
            return Optional.of("call to '" + sym.name
                    + "' on a receiver whose static type is not a class"
                    + " is not yet supported");
        }
        Set<String> signatures = new LinkedHashSet<>();
        boolean allOnReceiverClass = true;
        try {
            for (com.sun.tools.javac.code.Type supertype : javacTypes.closure(receiver.type)) {
                if (!(supertype.tsym instanceof Symbol.ClassSymbol cls)) {
                    continue;
                }
                boolean isReceiverOwnClass = cls == receiver;
                for (Symbol member : cls.members().getSymbolsByName(sym.name)) {
                    if (!(member instanceof Symbol.MethodSymbol candidate)
                            || (candidate.flags() & Flags.SYNTHETIC) != 0
                            || optedOutMethods.contains(candidate)) {
                        continue;
                    }
                    if (!isReceiverOwnClass && (candidate.flags() & Flags.PRIVATE) != 0) {
                        continue;
                    }
                    if (signatures.add(javacTypes.erasure(candidate.type)
                            .getParameterTypes().toString()) && !isReceiverOwnClass) {
                        allOnReceiverClass = false;
                    }
                }
            }
        } catch (Symbol.CompletionFailure e) {
            return Optional.of("supertype of the receiver of '" + sym.name
                    + "' could not be loaded, so overloading cannot be ruled out");
        }
        if (signatures.size() > 1) {
            return Optional.of(
                    "overloaded method '" + sym.name + "' ("
                            + signatures.size() + " signatures visible on "
                            + (allOnReceiverClass
                                    ? "'" + receiver.getQualifiedName() + "'"
                                    : "'" + receiver.getQualifiedName() + "' and its supertypes")
                            + "); overloading is not supported");
        }
        return Optional.empty();
    }

    // There is deliberately no dispatchRefusal here any more. It refused any call
    // that was not provably monomorphic, on the grounds that an `obj#method` call
    // resolved against the receiver's DECLARED type -- so a call reaching an
    // override at runtime would verify against the wrong contract. Laurel now
    // dispatches an overridden composite method on the receiver's runtime tag
    // (LiftInstanceProcedures), and CheckOverrideRefinement discharges the
    // Liskov obligation that makes reasoning against the declared type's
    // contract sound, so the premise no longer holds. PolyDispatchSoundness pins
    // both halves of the replacement.

    /** Turn a reported refusal into the exception that demotes the method. The gates
        report rather than throw so they stay pure and unit-testable; this is the single
        place a report becomes control flow. */
    static void enforce(Optional<String> refusal) {
        if (refusal.isPresent()) {
            throw new JavaViolationException(refusal.get());
        }
    }

    /** Refuse an instance call whose RECEIVER SHAPE cannot be lowered.
        <ul>
        <li>{@code super.m()} -- resolution walks the receiver's declared type UPWARD, so it
        cannot name "the parent's m, skipping the override".</li>
        <li>{@code new T().m()} -- a {@code new} receiver converts to an opaque allocation
        with no procedure-typed shape to pass as the receiver, so Strata fails to unify the
        argument. Refusable until such a value can be captured into a temporary.</li>
        </ul>
        Parentheses are unwrapped first, so {@code (new T()).m()} is caught too. An
        implicit-this call has no receiver expression and is always fine. */
    static Optional<String> receiverRefusal(JCTree.JCExpression methodSelect) {
        if (methodSelect instanceof JCTree.JCFieldAccess fieldAccess) {
            var selected = fieldAccess.selected;
            if (selected instanceof JCTree.JCIdent ident
                    && ident.name == ident.name.table.names._super) {
                return Optional.of("super call is not yet supported");
            }
            var unwrapped = selected;
            while (unwrapped instanceof JCTree.JCParens parens) {
                unwrapped = parens.expr;
            }
            if (unwrapped instanceof JCTree.JCNewClass) {
                return Optional.of("method call on a freshly-allocated receiver is not yet supported");
            }
        }
        return Optional.empty();
    }
}
