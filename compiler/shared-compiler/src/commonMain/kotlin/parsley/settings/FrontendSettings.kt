package parsley.settings

import parsley.Predicate
import parsley.collections.IntMap
import parsley.frontend.InsertLetStep
import parsley.frontend.LetFinderStep
import parsley.frontend.OptimiseStage
import parsley.frontend.ParserF
import parsley.frontend.RelabelStep
import parsley.frontend.Single
import parsley.frontend.TransformStep

data class FrontendSettings<I, E>(
    val letFinderSteps: List<LetFinderStep<I, E>>,
    val insertLetSteps: List<InsertLetStep<I, E>>,
    val relabelSteps: List<RelabelStep<I, E>>,
    val optimiseSteps: Map<OptimiseStage, List<TransformStep<I, E>>>,
    val rebuildPredicate: RebuildPredicate<I>
)

data class SubParsers<I, E>(private val parsers: IntMap<ParserF<I, E, Any?>>) {
    operator fun get(label: Int): ParserF<I, E, Any?> = parsers[label]
    operator fun set(label: Int, p: ParserF<I, E, Any?>): Unit {
        parsers[label] = p
    }

    fun size(): Int = parsers.size()
    fun <A> mapValues(f: (ParserF<I, E, Any?>) -> A): IntMap<A> = parsers.mapValues(f)
    fun forEach(f: (Int, ParserF<I, E, Any?>) -> Unit): Unit = parsers.forEach(f)
    fun find(p: ParserF<I, E, Any?>): Int? {
        var ind = -1
        parsers.forEach { i, parserF -> if (p === parserF) ind = i }
        return ind.takeIf { it != -1 }
    }
}

class RebuildPredicate<I>(
    val f: (sing: Array<Single<I>>, sat: Array<Predicate<I>>) -> Predicate<I> = { sing, sat ->
        Predicate { i -> sing.any { it.i == i } || sat.any { it(i) } }
    }
)

fun <I, E> CompilerSettings<I, E>.addLetFindStep(letStep: LetFinderStep<I, E>): CompilerSettings<I, E> =
    copy(frontend = frontend.copy(letFinderSteps = listOf(letStep) + frontend.letFinderSteps))

fun <I, E> CompilerSettings<I, E>.addLetInsertStep(letStep: InsertLetStep<I, E>): CompilerSettings<I, E> =
    copy(frontend = frontend.copy(insertLetSteps = listOf(letStep) + frontend.insertLetSteps))

fun <I, E> CompilerSettings<I, E>.addRelabelStep(step: RelabelStep<I, E>): CompilerSettings<I, E> =
    copy(frontend = frontend.copy(relabelSteps = listOf(step) + frontend.relabelSteps))

fun <I, E> CompilerSettings<I, E>.setRebuildPredicate(f: RebuildPredicate<I>): CompilerSettings<I, E> =
    copy(frontend = frontend.copy(rebuildPredicate = f))
