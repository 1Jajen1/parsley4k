package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Label
import parsley.backend.instructions.PushChunkOf
import parsley.backend.withDiscard
import parsley.frontend.ChunkOf

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> chunkOfGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is ChunkOf) {
        if (ctx.discard) {
            callRecursive(p.inner)
        } else {
            val handler = ctx.mkLabel()
            ctx += InputCheck(handler)
            ctx.withDiscard {
                callRecursive(p.inner)
            }
            ctx += Label(handler)
            ctx += PushChunkOf()
        }
        true
    } else false
}
