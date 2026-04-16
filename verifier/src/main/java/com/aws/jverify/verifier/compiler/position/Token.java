package com.aws.jverify.verifier.compiler.position;

public record Token(int line, int col) {
    public int getLine() { return line; }
    public int getCol() { return col; }
}
