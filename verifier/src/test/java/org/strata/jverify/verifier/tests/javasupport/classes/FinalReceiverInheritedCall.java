package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A method on non-final base {@code B}, invoked through a receiver of
 * {@code final} subclass {@code C}, is provably monomorphic: {@code C} being final
 * means the runtime type is exactly {@code C}, so {@code B.value()} is the only method
 * that can run. {@code refuseIfPolymorphicDispatch} accepts it via the
 * receiver-static-type-final disjunct, even though neither the callee nor its declaring
 * class {@code B} is final. Without that disjunct {@code c.value()} would be skipped
 * (the {@link ClassesExtendingClassesVerification} conservative case).
 *
 * Verified count 5 = B.value + root() + one implicit constructor per class
 * (FinalReceiverInheritedCall, B, C). Nothing skipped: the only call site is
 * accepted, and bodies are always emitted. {@code @Unbounded} avoids the
 * unrelated bounded-int overflow obligation.
 */
@JVerifyTest(methodsVerified = 5, methodsSkipped = 0, errorCount = 0)
public class FinalReceiverInheritedCall {

    void root() {
        C c = new C();
        // Receiver static type C is final -> monomorphic -> verified, not skipped.
        check(c.value() == 7);
    }
}

class B {
    @Pure
    @Unbounded
    int value() {
        postcondition((int r) -> r == 7);
        return 7;
    }
}

final class C extends B {
}
