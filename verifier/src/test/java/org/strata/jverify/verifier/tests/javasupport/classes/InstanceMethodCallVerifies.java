package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Positive CALL-SITE coverage for native {@code obj#method} emission. Both
 * receiver forms: explicit ({@code f.addOne(1)}) and implicit-this
 * ({@code addOne(a)}, forwarding the caller's own {@code self?}). The frontend
 * emits {@code obj#method(args)}; Strata's {@code LiftInstanceProcedures} moves
 * the receiver into the first argument position, binding it to the callee's
 * {@code self?}. The callee's postcondition proves the checks, so a mis-mapped or
 * mis-positioned {@code self} fails verification. The class is {@code final} so
 * calls are monomorphic (pass refuseIfPolymorphicDispatch); {@code @Unbounded}
 * avoids the unrelated bounded-int overflow obligation. Verified 4 = 3 methods +
 * implicit constructor. Declaration-side twin: {@link InstanceMethodVerifies}.
 */
@JVerifyTest(methodsVerified = 4, errorCount = 0)
final class InstanceMethodCallVerifies {

    @Unbounded
    int addOne(@Unbounded int x) {
        postcondition((int r) -> r == x + 1);
        return x + 1;
    }

    // Implicit-this call: the caller's own `self` forwards as addOne's receiver.
    @Unbounded
    int viaThis(@Unbounded int a) {
        check(addOne(a) == a + 1);
        return a;
    }

    // Explicit receiver: emitted as `f#addOne(1)`; the lifting pass binds `f` to
    // addOne's `self?` parameter.
    static void viaReceiver(InstanceMethodCallVerifies f) {
        check(f.addOne(1) == 2);
    }
}
