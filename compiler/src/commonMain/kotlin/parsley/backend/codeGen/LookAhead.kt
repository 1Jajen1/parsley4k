package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Label
import parsley.backend.instructions.ResetOffset
import parsley.frontend.LookAhead

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> lookAheadGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is LookAhead) {
        val l = ctx.mkLabel()
        ctx += InputCheck(l)
        callRecursive(p.inner)
        ctx += Label(l)
        ctx += ResetOffset()
        true
    } else false
}
