package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.6 -- Text Blocks: an unescaped double-quote character inside a
// text block is legal (only a sequence of three would close the block).
class TextBlocks_3 {
    static void run() {

        String s = """
                say "hi"
                """;
        check(s.equals("say \"hi\"\n"));
    }
}
