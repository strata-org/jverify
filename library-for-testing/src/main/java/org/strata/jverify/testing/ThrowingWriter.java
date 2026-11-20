package org.strata.jverify.testing;

public class ThrowingWriter {
    public static void writeThenThrow(Object o) throws Exception {
        throw new Exception();
    }
}
