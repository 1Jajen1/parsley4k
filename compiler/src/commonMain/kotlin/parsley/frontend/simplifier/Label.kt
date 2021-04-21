package parsley.frontend.simplifier

import parsley.ErrorItem
import parsley.frontend.Alt
import parsley.frontend.Ap
import parsley.frontend.Binary
import parsley.frontend.Fail
import parsley.frontend.FailTop
import parsley.frontend.Label
import parsley.frontend.Many
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.RelabelStep
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.frontend.TransformStep
import parsley.frontend.Unary
import parsley.settings.CompilerSettings
import parsley.unsafe

fun <I, E> relabel(): TransformStep<I, E> = TransformStep { p, _, settings ->
    if (p is Label) {
        p.inner.relabel(settings, p.label)
    } else p
}

@OptIn(ExperimentalStdlibApi::class)
private fun <I, E> ParserF<I, E, Any?>.relabel(
    settings: CompilerSettings<I, E>,
    label: String?
): ParserF<I, E, Any?> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { pI ->
        settings.frontend.relabelSteps.fold(null as ParserF<I, E, Any?>?) { acc, s ->
            acc ?: s.run { step(pI, label) }
        } ?: throw IllegalStateException("No step could relabel")
    }.invoke(this)

class DefaultRelabelStep<I, E> : RelabelStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        lbl: String?
    ): ParserF<I, E, Any?>? {
        return when (p) {
            is Alt -> Alt(callRecursive(p.first), callRecursive(p.second))
            is Satisfy<*> -> Satisfy<I>(
                p.match.unsafe(),
                lbl?.let { setOf(ErrorItem.Label(it)) } ?: emptySet(),
                p.accepts.unsafe(), p.rejects.unsafe()
            )
            is Single<*> -> Single<I>(
                p.i.unsafe(),
                lbl?.let { setOf(ErrorItem.Label(it)) } ?: emptySet()
            )
            is Ap<I, E, *, Any?> ->
                if (p.first is Pure) Ap(p.first, callRecursive(p.second).unsafe())
                else Ap(callRecursive(p.first).unsafe(), p.second)
            is Many<I, E, *>, is Pure, is Fail, is FailTop -> p
            // TODO double check if I need more exceptions here
            is Unary<I, E, *, Any?> -> p.copy(callRecursive(p.inner).unsafe())
            is Binary<I, E, *, *, Any?> -> p.copy(callRecursive(p.first).unsafe(), p.second.unsafe())
            // Why not relabel Let by relabelling the referenced parser?
            // Well the problem is multiple labeled parsers may use the same let bound one, hence we can't
            // guarantee our label remains the only one and we'd overwrite labels.
            // is Let -> ???
            else -> null.also { println(p) }
        }
    }
}
