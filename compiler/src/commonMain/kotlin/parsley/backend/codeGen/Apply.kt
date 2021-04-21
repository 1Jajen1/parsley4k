package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.instructions.Apply
import parsley.backend.instructions.Map
import parsley.backend.withDiscard
import parsley.frontend.Ap
import parsley.frontend.ApL
import parsley.frontend.ApR
import parsley.frontend.Pure
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> applyGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Ap<I, E, *, Any?>) {
        val fst = p.first
        if (fst is Pure) {
            callRecursive(p.second)
            if (!ctx.discard) ctx += Map(fst.a.unsafe())
        } else {
            callRecursive(fst)
            callRecursive(p.second)
            if (!ctx.discard) ctx += Apply()
        }
        true
    } else false
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> applyLGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is ApL<I, E, Any?, *>) {
        callRecursive(p.first)
        ctx.withDiscard {
            callRecursive(p.second)
        }
        true
    } else false
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> applyRGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is ApR<I, E, *, Any?>) {
        ctx.withDiscard {
            callRecursive(p.first)
        }
        callRecursive(p.second)
        true
    } else false
}
