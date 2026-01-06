package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.laurel.Node;

import java.net.URI;
import java.util.List;

public record LaurelFile(URI uri, Node root, List<Integer> lineOffsets) {}
