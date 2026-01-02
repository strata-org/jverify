package com.aws.jverify.verifier.dafny;

public interface Encoder {
    void writeBool(boolean value);

    void writeInt(int value);
    void writeLong(long value);

    void writeQualifiedName(String name);

    void writeString(String value);
    void writeNullable(boolean isNull);

    void writeDouble(double d);
}


