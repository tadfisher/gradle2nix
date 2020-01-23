package org.nixos.gradle2nix

import java.io.PrintStream

class Logger(
    val out: PrintStream = System.err,
    val verbose: Boolean
) {

    val log: (String) -> Unit = { if (verbose) out.println(it) }
    val warn: (String) -> Unit = { out.println("Warning: $it")}
    val error: (String) -> Unit = {
        out.println("Error: $it")
        System.exit(1)
    }

    operator fun component1() = log
    operator fun component2() = warn
    operator fun component3() = error
}
