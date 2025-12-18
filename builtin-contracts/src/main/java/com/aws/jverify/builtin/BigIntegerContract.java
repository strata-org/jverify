package com.aws.jverify.builtin;

import com.aws.jverify.*;
import java.math.BigInteger;
import static com.aws.jverify.JVerify.*;

@Contract(value = BigInteger.class, pure = true)
class BigIntegerContract  {
    // Field holding the value of the BigInteger
    @Unbounded int intValue;

    BigIntegerContract(String val) {
        precondition(HelperForBigIntegerContract.isValidString(val));
        postcondition(intValue == HelperForBigIntegerContract.stringToInt(val));
    }

    @Pure
    int intValue() {
        precondition(intValue >= Integer.MIN_VALUE && intValue <= Integer.MAX_VALUE);
        postcondition((int b) -> b == intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue + v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract subtract(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue - v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract multiply(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue * v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract divide(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue / v.intValue);
        throw new ContractException();
    }

    @Pure
    int compareTo(BigIntegerContract v) {
        postcondition((int r) -> (r < 0 && this.intValue < v.intValue) || (r == 0 && this.intValue == v.intValue) || (r > 0 && this.intValue > v.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract abs() {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < 0 ? -this.intValue : this.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract min(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract max(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue > v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract mod(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue % v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract negate() {
        postcondition((BigIntegerContract b) -> b.intValue == -this.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract pow(int exponent) {
        precondition(exponent >= 0);
        postcondition((BigIntegerContract b) -> b.intValue == HelperForBigIntegerContract.pow(intValue, exponent));
        throw new ContractException();
    }

    @Pure
    static BigIntegerContract valueOf(long val) {
        postcondition((BigIntegerContract b) -> b.intValue == val);
        throw new ContractException();
    }

    @Pure
    public int signum() {
        postcondition((int b) -> b == (intValue < 0 ? -1 : intValue == 0 ? 0 : 1));
        throw new ContractException();
    }

}