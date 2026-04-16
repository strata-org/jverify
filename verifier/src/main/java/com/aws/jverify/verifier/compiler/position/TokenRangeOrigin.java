package com.aws.jverify.verifier.compiler.position;

public final class TokenRangeOrigin implements IOrigin {
    private final Token startToken;
    private final Token endToken;

    public TokenRangeOrigin(Token startToken, Token endToken) {
        this.startToken = startToken;
        this.endToken = endToken;
    }

    public Token startToken() { return startToken; }
    public Token endToken() { return endToken; }
}
