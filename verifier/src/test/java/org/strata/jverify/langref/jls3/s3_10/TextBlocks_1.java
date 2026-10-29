package org.strata.jverify.langref.jls3.s3_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.6 -- Text Blocks: triple-quoted multi-line strings with incidental whitespace stripping
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_10.TextBlocks_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class TextBlocks_1 {
    static void run() {

        String block = """
                line1
                line2
                """;
        check(block.equals("line1\nline2\n"));
    }
}
