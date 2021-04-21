package parsley.backend.codeGen

import parsley.ParseErrorT
import parsley.backend.CodeGenStep
import parsley.backend.instructions.Apply
import parsley.backend.instructions.FailIfLeft
import parsley.backend.instructions.FailIfLeftTop
import parsley.backend.instructions.Flip
import parsley.backend.instructions.JumpIfRight
import parsley.backend.instructions.Label
import parsley.backend.instructions.Pop
import parsley.backend.withDiscard
import parsley.frontend.Empty
import parsley.frontend.FailTop
import parsley.frontend.Select

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> selectGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Select<I, E, *, Any?>) {
        ctx.withDiscard(false) {
            callRecursive(p.first)
        }
        when (val left = p.second) {
            Empty -> {
                ctx += FailIfLeft()
                if (ctx.discard) ctx += Pop() // TODO
            }
            is parsley.frontend.Fail -> {
                ctx += FailIfLeft(ParseErrorT.fromFinal(left.err))
                if (ctx.discard) ctx += Pop() // TODO
            }
            FailTop -> {
                ctx += FailIfLeftTop()
                if (ctx.discard) ctx += Pop()
            }
            else -> {
                val rightLabel = ctx.mkLabel()
                ctx += JumpIfRight(rightLabel)
                callRecursive(left)
                if (!ctx.discard) {
                    ctx += Flip()
                    ctx += Apply()
                }
                ctx += Label(rightLabel)
            }
        }
        true
    } else false
}
