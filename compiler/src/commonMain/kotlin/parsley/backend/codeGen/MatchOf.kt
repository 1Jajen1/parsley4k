package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Label
import parsley.backend.instructions.MkPair
import parsley.backend.instructions.PushChunkOf
import parsley.frontend.MatchOf

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> matchOfGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is MatchOf<I, E, *>) {
        if (ctx.discard) {
            callRecursive(p.inner)
        } else {
            val handler = ctx.mkLabel()
            ctx += InputCheck(handler)
            callRecursive(p.inner)
            ctx += Label(handler)
            ctx += PushChunkOf()
            ctx += MkPair()
        }
        true
    } else false
}
