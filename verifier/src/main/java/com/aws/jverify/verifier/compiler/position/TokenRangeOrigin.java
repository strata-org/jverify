package com.aws.jverify.verifier.compiler.position;

public class TokenRangeOrigin extends IOrigin {
    private final Token startToken;
    private final Token endToken;

    public TokenRangeOrigin(Token startToken, Token endToken) {
        this.startToken = startToken;
        this.endToken = endToken;
    }

    public Token getStartToken() { return startToken; }
    public Token getEndToken() { return endToken; }
}
