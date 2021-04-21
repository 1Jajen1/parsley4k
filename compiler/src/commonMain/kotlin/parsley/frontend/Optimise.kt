package parsley.frontend

import parsley.AnyParser
import parsley.settings.CompilerSettings
import parsley.settings.SubParsers
import parsley.unsafe
import pretty.Doc
import pretty.hSep
import pretty.nest
import pretty.punctuate
import pretty.text

fun <I, E, A> ParserF<I, E, A>.optimise(
    subs: SubParsers<I, E>,
    steps: Map<OptimiseStage, List<TransformStep<I, E>>>,
    settings: CompilerSettings<I, E>
): ParserF<I, E, A> {
    val idStep: List<TransformStep<I, E>> = listOf(TransformStep { p, _, _ -> p })

    val normaliseSteps = steps[OptimiseStage.Normalise] ?: idStep
    val pNormalized = onEach(this) { p ->
        normaliseSteps.fold(p) { acc, (s) -> s(acc, subs, settings) }
    }

    val simplifierSteps = steps[OptimiseStage.Simplifier] ?: idStep
    val pSimplified = onEach(pNormalized) { p ->
        simplifierSteps.fold(p) { acc, (s) -> s(acc, subs, settings) }
    }

    return pSimplified.unsafe()
}

@OptIn(ExperimentalStdlibApi::class)
private inline fun <I, E> onEach(
    parser: AnyParser<I, E>,
    crossinline f: suspend DeepRecursiveScope<AnyParser<I, E>, AnyParser<I, E>>.(AnyParser<I, E>) -> AnyParser<I, E>
): AnyParser<I, E> = DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
    val p = when (p) {
        is Unary<I, E, *, Any?> -> p.copy(callRecursive(p.inner).unsafe())
        is Binary<I, E, *, *, Any?> -> p.copy(callRecursive(p.first).unsafe(), callRecursive(p.second).unsafe())
        else -> p
    }
    f(p)
}(parser)
