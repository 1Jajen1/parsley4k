package parsley.backend.codeGen

import parsley.backend.CodeGenStep
import parsley.backend.codeGen.alt.altDefaultGen
import parsley.backend.codeGen.alt.altJumpTableGen
import parsley.backend.codeGen.alt.altOfSingleGen
import parsley.frontend.Alt
import parsley.frontend.ParserF

fun <I, E> altGen(): CodeGenStep<I, E> = CodeGenStep { p, ctx, settings ->
    if (p is Alt) {
        val alternatives = toAltList(p)

        altOfSingleGen(alternatives, ctx, settings)
            ?: altJumpTableGen(alternatives, ctx)
            ?: altDefaultGen(alternatives, ctx)
        true
    } else false
}

private fun <I, E> toAltList(
    p: Alt<I, E, Any?>
): List<ParserF<I, E, Any?>> {
    val buf = mutableListOf<ParserF<I, E, Any?>>()
    var curr: ParserF<I, E, Any?> = p
    while (curr is Alt) {
        buf.add(curr.first)
        curr = curr.second
    }
    buf.add(curr)
    return buf
}
