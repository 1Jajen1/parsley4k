package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.frontend.Label

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> labelGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Label) {
        // TODO
        println("Generated label")
        callRecursive(p.inner)
        true
    } else false
}
