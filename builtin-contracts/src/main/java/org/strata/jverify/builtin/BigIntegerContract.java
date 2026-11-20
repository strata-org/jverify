package org.strata.jverify.builtin;

import org.strata.jverify.*;
import java.math.BigInteger;
import static org.strata.jverify.JVerify.*;

@Contract(value = BigInteger.class, pure = true)
class BigIntegerContract  {
    // Field holding the value of the BigInteger
    @Unbounded int intValue;

    BigIntegerContract(String val) {
        precondition(HelperForBigIntegerContract.isValidString(val));
        postcondition(intValue == HelperForBigIntegerContract.stringToInt(val));
    }

    int intValue() {
        precondition(intValue >= Integer.MIN_VALUE && intValue <= Integer.MAX_VALUE);
        postcondition((int b) -> b == intValue);
        throw new ContractException();
    }

    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue + v.intValue);
        throw new ContractException();
    }

    BigIntegerContract subtract(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue - v.intValue);
        throw new ContractException();
    }

    BigIntegerContract multiply(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue * v.intValue);
        throw new ContractException();
    }

    BigIntegerContract divide(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue / v.intValue);
        throw new ContractException();
    }

    int compareTo(BigIntegerContract v) {
        postcondition((int r) -> (r < 0 && this.intValue < v.intValue) || (r == 0 && this.intValue == v.intValue) || (r > 0 && this.intValue > v.intValue));
        throw new ContractException();
    }

    BigIntegerContract abs() {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < 0 ? -this.intValue : this.intValue));
        throw new ContractException();
    }

    BigIntegerContract min(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    BigIntegerContract max(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue > v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    BigIntegerContract mod(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue % v.intValue);
        throw new ContractException();
    }

    BigIntegerContract negate() {
        postcondition((BigIntegerContract b) -> b.intValue == -this.intValue);
        throw new ContractException();
    }

    BigIntegerContract pow(int exponent) {
        precondition(exponent >= 0);
        postcondition((BigIntegerContract b) -> b.intValue == HelperForBigIntegerContract.pow(intValue, exponent));
        throw new ContractException();
    }

    static BigIntegerContract valueOf(long val) {
        postcondition((BigIntegerContract b) -> b.intValue == val);
        throw new ContractException();
    }

    public int signum() {
        postcondition((int b) -> b == (intValue < 0 ? -1 : intValue == 0 ? 0 : 1));
        throw new ContractException();
    }

}