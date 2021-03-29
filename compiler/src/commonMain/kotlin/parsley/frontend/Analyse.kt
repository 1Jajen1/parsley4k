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

@OptIn(ExperimentalStdlibApi::class)
inline fun <I, E> LetBoundStep.Companion.fallthrough(crossinline f: suspend DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.(p: ParserF<I, E, Any?>, PersistentSet<ParserF<I, E, Any?>>) -> Boolean) =
    object : LetBoundStep<I, E> {
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
                return if (f(p, nS)) true else {
                    // ugly
                    refCount[p] = refCount[p]!! - 1
                    seen.remove(p)
                    false
                }
            }

            return false
        }
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
                    callRecursive(nS to p.f.invoke())
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

@OptIn(ExperimentalStdlibApi::class)
inline fun <I, E> InsertLetStep.Companion.fallthrough(
    crossinline f: suspend DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.(ParserF<I, E, Any?>) -> ParserF<I, E, Any?>?,
    crossinline isSmall: (ParserF<I, E, Any?>) -> Boolean
): InsertLetStep<I, E> =
    object : InsertLetStep<I, E> {
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

            return if (bound && !isSmall(p)) {
                val newLabel =
                    if (handled.containsKey(p)) handled[p]!!
                    else {
                        when (val new = f(p)) {
                            null -> return null
                            else -> {
                                mkLabel().also {
                                    subParsers[it] = new
                                    handled[p] = it
                                }
                            }
                        }
                    }
                Let(rec, newLabel)
            } else f(p)
        }
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
                        callRecursive(p.f.invoke())
                    else p
                }
                else -> p
            }

        return if (bound && !p.small()) {
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
}

// TODO
fun <I, E, A> ParserF<I, E, A>.small(): Boolean = when (this) {
    is Pure, is Satisfy<*>, is Single<*>, Empty, is Label, Eof -> true
    is Ap<I, E, *, A> -> first.small() && second.small()
    is ApR<I, E, *, A> -> first.small() && second.small()
    is ApL<I, E, A, *> -> first.small() && second.small()
    is ChunkOf -> inner.small()
    is MatchOf<I, E, *> -> inner.small()
    else -> false
}
