package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Label
import parsley.backend.instructions.ResetOffsetOnFail
import parsley.frontend.Attempt

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> attemptGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Attempt) {
        val l = ctx.mkLabel()
        ctx += InputCheck(l)
        callRecursive(p.inner)
        ctx += Label(l)
        ctx += ResetOffsetOnFail()
        true
    } else false
}
