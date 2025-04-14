package com.aws.jverify;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.aws.jverify.TestUtilities.*;

public class VerificationConstructTests {
    @Test
    public void freshAndOld() throws IOException {
        verifyMarkedSourceFile("FreshAndOld.java", new DafnyResults(2, 0));
    }
}
