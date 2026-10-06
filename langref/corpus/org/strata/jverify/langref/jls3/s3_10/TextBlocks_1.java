package org.strata.jverify.langref.jls3.s3_10;

import static org.strata.jverify.JVerify.*;

// JLS 3.10.6 -- Text Blocks: triple-quoted multi-line strings with incidental whitespace stripping
class TextBlocks_1 {
    static void run() {

        String block = """
                line1
                line2
                """;
        check(block.equals("line1\nline2\n"));
    }
}
