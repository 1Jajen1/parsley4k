package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Label
import parsley.backend.instructions.Push
import parsley.backend.instructions.ResetOnFailAndFailOnOk
import parsley.backend.withDiscard
import parsley.frontend.NegLookAhead

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> negLookAheadGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is NegLookAhead) {
        val badLabel = ctx.mkLabel()
        ctx += InputCheck(badLabel)
        ctx.withDiscard {
            callRecursive(p.inner)
        }
        ctx += Label(badLabel)
        ctx += ResetOnFailAndFailOnOk()
        if (!ctx.discard) ctx += Push(Unit)
        true
    } else false
}
