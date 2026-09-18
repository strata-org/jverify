package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

/**
 * Blocked on Laurel type parameters carrying no constraints
 * (STRATA_NO_TYPE_CONSTRAINTS).
 *
 * <p>The front end DOES translate this now: T is a METHOD type parameter, so it
 * becomes a Laurel {@code TVar} listed in the procedure's {@code typeArgs}. But
 * {@code @Impure Object o = value} is legal Java only because T's implicit bound
 * is {@code Object}, and Laurel's {@code typeArgs} is a list of bare identifiers
 * with no bound slot -- so the front end cannot say {@code T extends Object} and
 * emits the assignment unjustified.
 *
 * <p>Laurel then rejects it, CORRECTLY: a bare {@code TVar} is universally
 * quantified, a caller could instantiate T at {@code int}, so assigning it to a
 * composite is unsound. The emitted Laurel is genuinely ill-typed --
 *
 * <pre>
 * composite Obj { var dummy: int }
 * procedure objectIsTop&lt;T&gt;(value: T) opaque { var o: Obj := value };
 * -&gt; Impossible to unify Composite with $__opaque_objectIsTop_T_0.
 * </pre>
 *
 * -- so this is a missing Laurel FEATURE (constrained type parameters), not a
 * Strata bug, and not something the front end can work around while the
 * constraint is inexpressible. Translating the type variable as its ERASURE
 * instead would always be well-typed, at the cost of the T-in/T-out link.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_NO_TYPE_CONSTRAINTS}, exitCode = 22)
public class PolymorphismResolutionErrors {
    
    public static <T> void modifiableObjectIsNotTop(T value) {
        @Impure Object o = value;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ImpureObject)
    }
}