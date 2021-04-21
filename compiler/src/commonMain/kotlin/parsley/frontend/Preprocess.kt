package parsley.frontend

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import parsley.AnyParser
import parsley.ErrorItem
import parsley.collections.IntMap
import parsley.settings.CompilerSettings
import parsley.settings.SubParsers
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.findLetBound(
    letFinderSteps: List<LetFinderStep<I, E>>
): Pair<Set<ParserF<I, E, Any?>>, Set<ParserF<I, E, Any?>>> {
    val refCount = mutableMapOf<ParserF<I, E, Any?>, Int>()
    val recursives = mutableSetOf<ParserF<I, E, Any?>>()

    DeepRecursiveFunction<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit> { (seen, p) ->
        letFinderSteps.forEach { (s) ->
            if (s(p, seen, refCount, recursives)) return@DeepRecursiveFunction
        }
    }(persistentHashSetOf<ParserF<I, E, Any?>>() to this.unsafe())
    return Pair(refCount.filter { (_, v) -> v > 1 }.keys, recursives)
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> defaultLetFinderStep(): LetFinderStep<I, E> = LetFinderStep { p, seen, refCount, recursives ->
    refCount[p].also { refCount[p] = it?.let { it + 1 } ?: 1 }

    if (seen.contains(p)) {
        recursives.add(p)
        true
    } else {
        if (refCount[p]!! == 1) {
            val nS = seen.add(p)
            when (p) {
                is Unary<I, E, *, Any?> -> {
                    callRecursive(nS to p.inner)
                }
                is Binary<I, E, *, *, Any?> -> {
                    callRecursive(nS to p.first)
                    callRecursive(nS to p.second)
                }
                is Lazy -> {
                    callRecursive(nS to p.p)
                }
                else -> Unit
            }
        }
        true
    }
}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.insertLets(
    letBound: Set<ParserF<I, E, Any?>>,
    recursive: Set<ParserF<I, E, Any?>>,
    insertLetSteps: List<InsertLetStep<I, E>>
): Triple<ParserF<I, E, A>, SubParsers<I, E>, Int> {
    var labelC = 0
    val subParsers = IntMap.empty<ParserF<I, E, Any?>>()
    val handled = mutableMapOf<ParserF<I, E, Any?>, Int>()

    val nP = DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        insertLetSteps.forEach { (s) ->
            val res = s(p, letBound, recursive, subParsers, handled) { labelC++ }
            if (res != null) return@DeepRecursiveFunction res
        }
        throw IllegalStateException("Let insertion did not provide a result")
    }(this)

    return Triple(nP.unsafe(), SubParsers(subParsers), labelC)
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> defaultInsertLetStep(): InsertLetStep<I, E> = InsertLetStep { p, letBound, recursive, subParsers, handled, mkLabel ->
    val bound = letBound.contains(p)
    val rec = recursive.contains(p)

    suspend fun <I, E> DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.new(
        p: ParserF<I, E, Any?>,
        handled: MutableMap<ParserF<I, E, Any?>, Int>
    ): ParserF<I, E, Any?> =
        when (p) {
            is Unary<I, E, *, Any?> -> {
                p.copy(
                    callRecursive(p.inner).unsafe()
                )
            }
            is Binary<I, E, *, *, Any?> -> {
                p.copy(
                    callRecursive(p.first).unsafe(),
                    callRecursive(p.second).unsafe()
                )
            }
            is Lazy -> {
                if (handled.containsKey(p).not())
                    callRecursive(p.p)
                else p // TODO wtf? This should always result in errors, why is this here?
            }
            else -> p
        }

    if (bound && !p.small()) {
        val newLabel =
            if (handled.containsKey(p)) handled[p]!!
            else {
                mkLabel().also {
                    handled[p] = it
                    subParsers[it] = new(p, handled)
                }
            }
        Let(rec, newLabel)
    } else new(p, handled)
}
