package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.6 -- Text Blocks: an unescaped double-quote character inside a
// text block is legal (only a sequence of three would close the block).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.TextBlocks_3#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TextBlocks_3 {
    static void run() {

        String s = """
                say "hi"
                """;
        check(s.equals("say \"hi\"\n"));
    }
}
