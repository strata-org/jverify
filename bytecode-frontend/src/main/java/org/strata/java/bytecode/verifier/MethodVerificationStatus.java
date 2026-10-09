package org.strata.java.bytecode.verifier;

import org.strata.jverify.common.Range;

/**
 * Per-method verification outcome, attributed from Strata diagnostics by
 * source position: the method's name and source range (recovered from the
 * classfile's line numbers / CharacterRangeTable).
 */
public class MethodVerificationStatus {
    private final String methodName;
    private final Range position;
    private VerificationStatus verificationStatus;

    public enum VerificationStatus {
        Skipped,
        Verified,
        Failed
    }

    public MethodVerificationStatus(String methodName, Range position, VerificationStatus status) {
        this.methodName = methodName;
        this.position = position;
        this.verificationStatus = status;
    }

    public String getMethodName() {
        return methodName;
    }

    public Range getPosition() {
        return position;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus status) {
        this.verificationStatus = status;
    }
}
