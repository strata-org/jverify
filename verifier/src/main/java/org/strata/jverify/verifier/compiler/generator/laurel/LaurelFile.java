package org.strata.jverify.verifier.compiler.generator.laurel;

import org.strata.jverify.laurel.Command;

import java.net.URI;
import java.util.List;

public record LaurelFile(URI uri, List<Command> commands) {}
