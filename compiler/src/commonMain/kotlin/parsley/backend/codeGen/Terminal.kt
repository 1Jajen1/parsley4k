package parsley.backend.codeGen

import parsley.ParseErrorT
import parsley.backend.CodeGenStep
import parsley.backend.instructions.Fail
import parsley.backend.instructions.Push
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.Single_
import parsley.frontend.Empty
import parsley.frontend.Eof
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.unsafe

fun <I, E> pureGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Pure) {
        if (!ctx.discard) ctx += Push(p.a)
        true
    } else false
}

fun <I, E> emptyGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Empty) {
        ctx += Fail()
        true
    } else false
}

fun <I, E> satisfyGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Satisfy<*>) {
        if (ctx.discard) ctx += Satisfy_(p.match.unsafe(), p.expected.unsafe())
        else ctx += parsley.backend.instructions.Satisfy(p.match.unsafe(), p.expected.unsafe())
        true
    } else false
}

fun <I, E> singleGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Single<*>) {
        if (ctx.discard) ctx += Single_(p.i.unsafe(), p.expected.unsafe())
        else ctx += parsley.backend.instructions.Single(p.i.unsafe(), p.expected.unsafe())
        true
    } else false
}

fun <I, E> failGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is parsley.frontend.Fail) {
        ctx += Fail(ParseErrorT.fromFinal(p.err))
        true
    } else false
}

fun <I, E> eofGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, _ ->
    if (p is Eof) {
        ctx += parsley.backend.instructions.Eof()
        true
    } else false
}
