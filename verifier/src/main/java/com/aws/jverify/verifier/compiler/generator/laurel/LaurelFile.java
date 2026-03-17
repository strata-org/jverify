package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.laurel.Command;

import java.net.URI;
import java.util.List;

public record LaurelFile(URI uri, List<Command> commands) {}
