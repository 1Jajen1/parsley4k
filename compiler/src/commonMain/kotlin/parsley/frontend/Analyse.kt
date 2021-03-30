package parsley.frontend

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import parsley.Either
import parsley.collections.IntMap
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.findLetBound(
    letsteps: Array<LetBoundStep<I, E>>
): Pair<Set<ParserF<I, E, Any?>>, Set<ParserF<I, E, Any?>>> {
    val refCount = mutableMapOf<ParserF<I, E, Any?>, Int>()
    val recursives = mutableSetOf<ParserF<I, E, Any?>>()

    DeepRecursiveFunction<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit> { (seen, p) ->
        letsteps.forEach { s ->
            s.run {
                if (step(p, seen, refCount, recursives)) return@DeepRecursiveFunction
            }
        }
    }(persistentHashSetOf<ParserF<I, E, Any?>>() to this)
    return Pair(refCount.filter { (_, v) -> v > 1 }.keys, recursives)
}

interface LetBoundStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.step(
        p: ParserF<I, E, Any?>,
        seen: PersistentSet<ParserF<I, E, Any?>>,
        refCount: MutableMap<ParserF<I, E, Any?>, Int>,
        recursives: MutableSet<ParserF<I, E, Any?>>
    ): Boolean

    companion object
}

class DefaultLetBoundStep<I, E> : LetBoundStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.step(
        p: ParserF<I, E, Any?>,
        seen: PersistentSet<ParserF<I, E, Any?>>,
        refCount: MutableMap<ParserF<I, E, Any?>, Int>,
        recursives: MutableSet<ParserF<I, E, Any?>>
    ): Boolean {
        refCount[p].also { refCount[p] = it?.let { it + 1 } ?: 1 }

        if (seen.contains(p)) {
            recursives.add(p)
            return true
        }

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
        return true
    }

}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.insertLets(
    letBound: Set<ParserF<I, E, Any?>>,
    recursive: Set<ParserF<I, E, Any?>>,
    insertLetSteps: Array<InsertLetStep<I, E>>
): Triple<ParserF<I, E, A>, IntMap<ParserF<I, E, Any?>>, Int> {
    var labelC = 0
    val subParsers = IntMap.empty<ParserF<I, E, Any?>>()
    val handled = mutableMapOf<ParserF<I, E, Any?>, Int>()

    val nP = DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        insertLetSteps.forEach { s ->
            s.run {
                val res = step(p, letBound, recursive, subParsers, handled) { labelC++ }
                if (res != null) return@DeepRecursiveFunction res
            }
        }
        throw IllegalStateException("Let insertion did not provide a result")
    }(this)

    return Triple(nP.unsafe(), subParsers, labelC)
}

interface InsertLetStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        letBound: Set<ParserF<I, E, Any?>>,
        recursive: Set<ParserF<I, E, Any?>>,
        subParsers: IntMap<ParserF<I, E, Any?>>,
        handled: MutableMap<ParserF<I, E, Any?>, Int>,
        mkLabel: () -> Int
    ): ParserF<I, E, Any?>?

    companion object
}

class DefaultInsertLetStep<I, E> : InsertLetStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        letBound: Set<ParserF<I, E, Any?>>,
        recursive: Set<ParserF<I, E, Any?>>,
        subParsers: IntMap<ParserF<I, E, Any?>>,
        handled: MutableMap<ParserF<I, E, Any?>, Int>,
        mkLabel: () -> Int
    ): ParserF<I, E, Any?>? {
        val bound = letBound.contains(p)
        val rec = recursive.contains(p)

        @OptIn(ExperimentalStdlibApi::class)
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

        return if (bound && !p.small()) {
            val newLabel =
                if (handled.containsKey(p)) handled[p]!!
                else {
                    mkLabel().also {
                        handled[p] = it
                        val subP = new(p, handled)
                        if (!rec && subP.small()) {
                            handled.remove(p)
                            return subP
                        }
                        subParsers[it] = subP
                    }
                }
            Let(rec, newLabel)
        } else new(p, handled)
    }
}
