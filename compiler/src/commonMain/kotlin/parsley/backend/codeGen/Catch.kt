package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.AlwaysRecover
import parsley.backend.instructions.CatchEither
import parsley.backend.instructions.Label
import parsley.backend.instructions.PushHandler
import parsley.frontend.Catch

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> catchGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Catch<I, E, *>) {
        val hdl = ctx.mkLabel()
        ctx += PushHandler(hdl)
        callRecursive(p.inner)
        ctx += Label(hdl)
        if (ctx.discard) {
            ctx += AlwaysRecover()
        } else {
            ctx += CatchEither()
        }
        true
    } else false
}
