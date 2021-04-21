package parsley.backend.codeGen.alt

import parsley.AnyParser
import parsley.backend.CodeGenContext
import parsley.backend.instructions.SatisfyNoFail
import parsley.backend.instructions.SingleNoFail
import parsley.frontend.Alt
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.settings.CompilerSettings
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
suspend fun <I, E> DeepRecursiveScope<AnyParser<I, E>, Unit>.altOfSingleGen(
    alternatives: List<ParserF<I, E, Any?>>,
    ctx: CodeGenContext<I, E>,
    settings: CompilerSettings<I, E>
): Unit? {
    val fst = alternatives[0]
    val snd = alternatives[1]
    // TODO properly benchmark this
    val singles = mutableListOf<Single<I>>()
    val satisfies = mutableListOf<Satisfy<I>>()
    alternatives.forEach { p ->
        if (p is Single<*>) singles.add(p.unsafe())
        else if (p is Satisfy<*>) satisfies.add(p.unsafe())
    }
    if (singles.size + satisfies.size == alternatives.size) {
        val func =
            settings.frontend.rebuildPredicate.f(singles.toTypedArray(), satisfies.map { it.match }.toTypedArray())
        val s = Satisfy(func)
        callRecursive(s)
        return Unit
    } else if (alternatives.size > 2 && alternatives.last() is Pure && singles.size + satisfies.size == alternatives.size - 1) {
        val func =
            settings.frontend.rebuildPredicate.f(singles.toTypedArray(), satisfies.map { it.match }.toTypedArray())
        val s = Alt(Satisfy(func), alternatives.last())
        callRecursive(s)
        return Unit
    }

    if (ctx.discard && alternatives.size == 2 && (fst is Single<*> || fst is Satisfy<*>) && snd is Pure) {
        when (fst) {
            is Single<*> -> ctx += SingleNoFail(fst.i.unsafe())
            is Satisfy<*> -> ctx += SatisfyNoFail(fst.match.unsafe())
        }
        return Unit
    }
    return null
}
