package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.Call
import parsley.frontend.Let

fun <I, E> letGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Let) {
        if (ctx.discard) {
            val l = ctx.discardSubParser(p.sub)
            ctx += Call(p.recursive, l)
        } else {
            ctx += Call(p.recursive, p.sub)
        }
        true
    } else false
}
