package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.JVerify;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * The four things {@code JVerify.Map} refuses, each with the diagnostic on the user's own text.
 *
 * <p>A refusal is a TRANSLATION error -- no Laurel is produced and no verdict is reached -- so
 * these cannot share a fixture with {@link MapsVerification}, whose cases run to a verdict.
 *
 * <p>A boxed primitive type argument is refused rather than unboxed: Strata does not accept a
 * constrained (subset) type such as {@code int32} as a generic datatype type argument, and leaving
 * it boxed strands the {@code Integer.valueOf}/{@code intValue} calls javac inserts around every
 * use. The message names {@code JVerify.IntMap}, which is the int-to-int map that does work --
 * see {@link IntMapReads}.
 *
 * <p>A user subtype is refused on the RECEIVER's static type rather than on the method's owner:
 * the owner of an inherited method is still {@code JVerify.Map}, while a subtype receiver lowers
 * to a composite instead of the Laurel map, so the map prelude call has nothing to resolve
 * against.
 */
@JVerifyTest(exitCode = 2)
class MapsError {

    static boolean boxedPrimitiveTypeArgument(JVerify.Map<Integer, Integer> m, Integer k) {
//                 ^ error: JVerify.Map does not support the boxed primitive type argument java.lang.Integer; use JVerify.IntMap for an int-to-int map
        return m.contains(k);
    }

    static int sizeHasNoLaurelCounterpart(JVerify.Map<String, String> m) {
//             ^ error: Unsupported JVerify.Map method: size
        return m.size();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean rawMapHasNoTypeArguments(JVerify.Map m, String k) {
//                 ^ error: JVerify.Map needs both type arguments: org.strata.jverify.JVerify.Map
        return m.contains(k);
    }

    interface StrMap extends JVerify.Map<String, String> {}

    static boolean subtypeIsNotTheLaurelMap(StrMap m, String k) {
//                 ^ error: JVerify map contains needs a JVerify.Map or JVerify.IntMap receiver, found: org.strata.jverify.verifier.tests.jcl.MapsError.StrMap
        return m.contains(k);
    }
}
