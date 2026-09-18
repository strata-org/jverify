package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A value of a subtype flows to any supertype-typed position, because each
 * composite sort is declared {@code extends} its Java supertype closure
 * (superclass chain + interfaces, transitively including {@code Object}) and
 * Strata's resolver walks those edges.
 *
 * <p>Regression: parentless sorts are unrelated on the lattice, so the resolver
 * rejects a flow Java's type system already proved — measured as
 * {@code expected 'Base', got 'Sub'}. Verified by removing the edges: this
 * fixture fails.
 *
 * <p>Covers argument, return, and local-init positions; a two-hop chain
 * ({@code Sub -> Base -> Root}); an interface edge ({@code Base implements
 * Tag}); {@code Object} from a class; and {@code Object} from an
 * INTERFACE-typed value. That last is its own case: javac gives an interface no
 * superclass edge, so its {@code Object} parent must be added explicitly
 * (JLS 4.10.2) or interface sorts come out parentless.
 */
/*
 * Verified 12 = the 4 @Pure helpers + retFlow + the 3 driver methods + one
 * implicit constructor each for SubtypeSortFlow, Root, Base, and Sub.
 */
@JVerifyTest(methodsVerified = 12, methodsSkipped = 0, errorCount = 0)
public class SubtypeSortFlow {

    interface Tag {}

    static class Root {}

    static class Base extends Root implements Tag {}

    static final class Sub extends Base {}

    @Pure @Unbounded static int takesRoot(Root r) { postcondition((int x) -> x == 1); return 1; }

    @Pure @Unbounded static int takesBase(Base b) { postcondition((int x) -> x == 2); return 2; }

    @Pure @Unbounded static int takesTag(Tag t) { postcondition((int x) -> x == 3); return 3; }

    @Pure @Unbounded static int takesObject(Object o) { postcondition((int x) -> x == 4); return 4; }

    @Pure static Base retFlow(Sub s) { return s; }

    static void argAndChain(Sub s) {
        check(takesBase(s) == 2);    // one hop
        check(takesRoot(s) == 1);    // two hops: Sub -> Base -> Root
        check(takesTag(s) == 3);     // interface edge, inherited from Base
        check(takesObject(s) == 4);  // Object from a class
    }

    static void localInit(Sub s) {
        Base b = s;
        check(takesBase(b) == 2);
    }

    static void objectFromInterface(Tag t) {
        // Interface-typed value reaching Object: the edge javac does not give.
        check(takesObject(t) == 4);
    }
}
