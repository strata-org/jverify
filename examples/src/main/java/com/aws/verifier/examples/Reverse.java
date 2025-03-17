package com.aws.verifier.examples;

import com.aws.jverify.JVerify;
import com.aws.jverify.Proof;
import com.aws.jverify.Pure;
import com.aws.jverify.Sequence;
import net.jqwik.api.*;

import java.util.stream.IntStream;

import static com.aws.jverify.JVerify.postcondition;

class StringReverserPropertyBased {

    String reverse(String input) {
        throw new RuntimeException("Not yet implemented");
    }

    @Property
    boolean reverseDefinition(@ForAll String original) {
        String reversed = reverse(original);
        return reversed.length() == original.length()
                && IntStream.range(0, original.length()).
                   allMatch(i -> original.charAt(i) == reversed.charAt(i));
    }

    @Property
    boolean reversingTwiceGivesOriginalString(@ForAll String original) {
        String reversed = reverse(original);
        String reversedTwice = reverse(reversed);

        return original.equals(reversedTwice);
    }
}

class StringReverserProven {
    String reverse(String original) {
        postcondition((String reversed) -> reversed.length() == original.length()
                && IntStream.range(0, original.length()).
                   allMatch(i -> original.charAt(i) == reversed.charAt(i)));

        throw new RuntimeException("Not yet implemented");
    }


    @Proof
    void reversingTwiceGivesOriginalString(String original) {
        postcondition(reverse(reverse(original)).equals(original));
        // proof
    }


    @Proof
    <T> void reversingTwiceGivesOriginaSequence(Sequence<T> original) {
        postcondition(reverse(reverse(original)).equals(original));
        // proof
    }
    
    @Pure
    <E> Sequence<E> reverse(Sequence<E> values) {
        if (values.size() == 0) {
            return Sequence.empty();
        } else {
            return reverse(values.drop(1)).concat(Sequence.single(values.get(0)));
        }
    }
}