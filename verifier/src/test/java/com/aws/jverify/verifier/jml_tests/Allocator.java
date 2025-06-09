package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class Allocator {
	public static byte[] alloc(int sizeXYZ) {
		precondition(sizeXYZ>=0);
		return new byte[sizeXYZ];
	}
}
